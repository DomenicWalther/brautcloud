package com.domenicwalther.brautcloud;

import com.domenicwalther.brautcloud.model.User;
import com.domenicwalther.brautcloud.repository.EventRepository;
import com.domenicwalther.brautcloud.repository.ImageRepository;
import com.domenicwalther.brautcloud.repository.RefreshTokenRepository;
import com.domenicwalther.brautcloud.repository.UserRepository;
import com.domenicwalther.brautcloud.service.JwtService;
import com.domenicwalther.brautcloud.support.FullStackIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthenticationIntegrationTest extends FullStackIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private EventRepository eventRepository;

	@Autowired
	private ImageRepository imageRepository;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtService jwtService;

	@BeforeEach
	void cleanDatabase() {
		refreshTokenRepository.deleteAll();
		imageRepository.deleteAll();
		eventRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void registrationLoginAndBearerAuthenticationWorkEndToEnd() throws Exception {
		mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(credentials()))
			.andExpect(status().isOk())
			.andExpect(content().string("User registered successfully"));

		User storedUser = userRepository.findByEmail("owner@example.com").orElseThrow();
		assertThat(storedUser.getPassword()).isNotEqualTo("correct-password");
		assertThat(passwordEncoder.matches("correct-password", storedUser.getPassword())).isTrue();

		MvcResult login = mockMvc
			.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").isNotEmpty())
			.andReturn();
		String accessToken = accessToken(login);
		Cookie refreshCookie = login.getResponse().getCookie("refresh_token");
		assertThat(refreshCookie).isNotNull();
		assertThat(refreshCookie.isHttpOnly()).isTrue();
		assertThat(refreshCookie.getPath()).isEqualTo("/api/auth");
		assertThat(login.getResponse().getHeader("Set-Cookie")).contains("SameSite=Strict");
		assertThat(refreshTokenRepository.findByToken(refreshCookie.getValue())).isPresent();

		mockMvc.perform(get("/api/user").header("Authorization", "Bearer " + accessToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value("owner@example.com"))
			.andExpect(jsonPath("$.events").isEmpty())
			.andExpect(jsonPath("$.password").doesNotExist());
	}

	@Test
	void duplicateRegistrationBadCredentialsAndAnonymousAccessAreRejected() throws Exception {
		register();

		mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(credentials()))
			.andExpect(status().isConflict())
			.andExpect(content().string("Email already used!"));

		mockMvc
			.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"owner@example.com\",\"password\":\"wrong-password\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error").value("Unauthorized"))
			.andExpect(jsonPath("$.message").value("Invalid email or password"));

		mockMvc.perform(get("/api/user")).andExpect(status().isForbidden());
		mockMvc.perform(get("/api/user").header("Authorization", "Bearer not-a-jwt")).andExpect(status().isForbidden());
		mockMvc
			.perform(get("/api/user").header("Authorization",
					"Bearer " + jwtService.generateToken("deleted@example.com")))
			.andExpect(status().isForbidden());
	}

	@Test
	void invalidAuthenticationPayloadIsRejectedBeforePersistence() throws Exception {
		mockMvc
			.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"not-an-email\",\"password\":\"short\"}"))
			.andExpect(status().isBadRequest());
		assertThat(userRepository.count()).isZero();

		mockMvc
			.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"owner@example.com\"}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void loginAllowsExistingShortPasswordAccounts() throws Exception {
		User legacyUser = new User();
		legacyUser.setEmail("legacy@example.com");
		legacyUser.setPassword(passwordEncoder.encode("short"));
		userRepository.save(legacyUser);

		mockMvc
			.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"legacy@example.com\",\"password\":\"short\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").isNotEmpty());
	}

	@Test
	void refreshRotatesSingleUseSessionAndLogoutRevokesIt() throws Exception {
		register();
		MvcResult login = login();
		Cookie originalCookie = login.getResponse().getCookie("refresh_token");

		MvcResult refresh = mockMvc.perform(post("/api/auth/refresh").cookie(originalCookie))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").isNotEmpty())
			.andReturn();
		Cookie rotatedCookie = refresh.getResponse().getCookie("refresh_token");
		assertThat(rotatedCookie.getValue()).isNotEqualTo(originalCookie.getValue());
		assertThat(refreshTokenRepository.findByToken(originalCookie.getValue())).isEmpty();
		assertThat(refreshTokenRepository.findByToken(rotatedCookie.getValue())).isPresent();
		assertThat(refreshTokenRepository.count()).isOne();

		mockMvc.perform(post("/api/auth/refresh").cookie(originalCookie))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("Refresh token not found"));

		MvcResult logout = mockMvc.perform(post("/api/auth/logout").cookie(rotatedCookie))
			.andExpect(status().isOk())
			.andExpect(content().string("Logged out"))
			.andReturn();
		assertThat(refreshTokenRepository.count()).isZero();
		assertThat(logout.getResponse().getCookie("refresh_token").getMaxAge()).isZero();
	}

	@Test
	void refreshRequiresCookie() throws Exception {
		mockMvc.perform(post("/api/auth/refresh")).andExpect(status().isBadRequest());
	}

	private void register() throws Exception {
		mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(credentials()))
			.andExpect(status().isOk());
	}

	private MvcResult login() throws Exception {
		return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials()))
			.andExpect(status().isOk())
			.andReturn();
	}

	private static String accessToken(MvcResult result) throws Exception {
		return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
	}

	private static String credentials() {
		return "{\"email\":\"owner@example.com\",\"password\":\"correct-password\"}";
	}

}
