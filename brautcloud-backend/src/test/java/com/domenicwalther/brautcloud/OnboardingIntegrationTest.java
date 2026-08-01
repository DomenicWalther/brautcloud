package com.domenicwalther.brautcloud;

import com.domenicwalther.brautcloud.model.Event;
import com.domenicwalther.brautcloud.model.User;
import com.domenicwalther.brautcloud.repository.EventRepository;
import com.domenicwalther.brautcloud.repository.RefreshTokenRepository;
import com.domenicwalther.brautcloud.repository.UserRepository;
import com.domenicwalther.brautcloud.service.JwtService;
import com.domenicwalther.brautcloud.support.FullStackIntegrationTest;
import com.domenicwalther.brautcloud.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OnboardingIntegrationTest extends FullStackIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private EventRepository eventRepository;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtService jwtService;

	@BeforeEach
	void cleanDatabase() {
		refreshTokenRepository.deleteAll();
		eventRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void onboardingRequiresAndPersistsEventDate() throws Exception {
		User owner = persistUser("owner@example.com");
		String token = jwtService.generateToken(owner.getEmail());

		mockMvc
			.perform(post("/api/onboarding").header("Authorization", bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content(
						"""
								{"firstName":"Sophie","partnerFirstName":"Marcus","familyName":"Müller-Weber","venue":"Eichenfürst","date":"2030-06-15T00:00:00"}
								"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.event.date").value("2030-06-15T00:00:00"));

		Event event = eventRepository.findByUser(owner).getFirst();
		assertThat(event.getDate()).isEqualTo(LocalDateTime.of(2030, 6, 15, 0, 0));
	}

	@Test
	void onboardingWithPasswordHashesAndPersistsIt() throws Exception {
		User owner = persistUser("owner@example.com");
		String token = jwtService.generateToken(owner.getEmail());

		mockMvc
			.perform(post("/api/onboarding").header("Authorization", bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content(
						"""
								{"firstName":"Sophie","partnerFirstName":"Marcus","familyName":"Müller-Weber","venue":"Eichenfürst","date":"2030-06-15T00:00:00","password":"Secret-Gallery9!"}
								"""))
			.andExpect(status().isOk());

		Event event = eventRepository.findByUser(owner).getFirst();
		assertThat(event.getPassword()).isNotNull();
		assertThat(passwordEncoder.matches("Secret-Gallery9!", event.getPassword())).isTrue();
	}

	@Test
	void onboardingWithoutPasswordCreatesOpenGallery() throws Exception {
		User owner = persistUser("owner@example.com");
		String token = jwtService.generateToken(owner.getEmail());

		mockMvc
			.perform(post("/api/onboarding").header("Authorization", bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content(
						"""
								{"firstName":"Sophie","partnerFirstName":"Marcus","familyName":"Müller-Weber","venue":"Eichenfürst","date":"2030-06-15T00:00:00"}
								"""))
			.andExpect(status().isOk());

		Event event = eventRepository.findByUser(owner).getFirst();
		assertThat(event.getPassword()).isNull();
	}

	@Test
	void onboardingRejectsMissingEventDateBeforeCreatingEvent() throws Exception {
		User owner = persistUser("owner@example.com");
		String token = jwtService.generateToken(owner.getEmail());

		mockMvc.perform(post("/api/onboarding").header("Authorization", bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"firstName":"Sophie","partnerFirstName":"Marcus","familyName":"Müller-Weber","venue":"Eichenfürst"}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Event date is required"));

		assertThat(eventRepository.findByUser(owner)).isEmpty();
	}

	private User persistUser(String email) {
		User user = TestFixtures.user(email);
		user.setPassword(passwordEncoder.encode("correct-password"));
		return userRepository.save(user);
	}

	private static String bearer(String token) {
		return "Bearer " + token;
	}

}
