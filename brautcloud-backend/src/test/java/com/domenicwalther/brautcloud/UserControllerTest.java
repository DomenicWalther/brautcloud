package com.domenicwalther.brautcloud;

import com.domenicwalther.brautcloud.dto.OnboardingRequest;
import com.domenicwalther.brautcloud.model.Event;
import com.domenicwalther.brautcloud.model.User;
import com.domenicwalther.brautcloud.repository.EventRepository;
import com.domenicwalther.brautcloud.repository.RefreshTokenRepository;
import com.domenicwalther.brautcloud.repository.UserRepository;
import com.domenicwalther.brautcloud.service.JwtService;
import com.domenicwalther.brautcloud.service.OnboardingService;
import com.domenicwalther.brautcloud.support.PostgresTestSupport;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserControllerTest {

	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(PostgresTestSupport.IMAGE);

	@LocalServerPort
	private Integer port;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private EventRepository eventRepository;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Autowired
	private OnboardingService onboardingService;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JwtService jwtService;

	@BeforeAll
	static void beforeAll() {
		POSTGRES.start();
	}

	@AfterAll
	static void afterAll() {
		POSTGRES.stop();
	}

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("jwt.token.secret",
				() -> "test-secret-that-is-deliberately-long-enough-for-hmac-sha-signing-123456789");
		registry.add("aws.accessKeyId", () -> "test-access-key");
		registry.add("aws.secretAccessKey", () -> "test-secret-key");
		registry.add("aws.s3.endpoint", () -> "http://localhost:9000");
		registry.add("aws.s3.region", () -> "us-east-1");
		registry.add("aws.s3.bucket", () -> "test-bucket");
	}

	@BeforeEach
	void setUp() {
		RestAssured.baseURI = "http://localhost:" + port;
		refreshTokenRepository.deleteAll();
		eventRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void freshRegistrationReturnsStructuredSessionForIncompleteUser() {
		Response response = register("new@example.com");

		response.then()
			.statusCode(200)
			.contentType(ContentType.JSON)
			.body("accessToken", not(nullValue()))
			.body("onboardingComplete", equalTo(false));
		assertNotNull(response.cookie("refresh_token"));

		User savedUser = userRepository.findByEmail("new@example.com").orElseThrow();
		assertNull(savedUser.getOnboardingCompletedAt());
		assertEquals(0, eventRepository.count());
	}

	@Test
	void interruptedRegistrationStaysIncompleteAcrossLoginAndRefresh() {
		Response registration = register("interrupted@example.com");

		given().cookie("refresh_token", registration.cookie("refresh_token"))
			.contentType(ContentType.JSON)
			.when()
			.post("/api/auth/refresh")
			.then()
			.statusCode(200)
			.body("onboardingComplete", equalTo(false));

		login("interrupted@example.com").then().statusCode(200).body("onboardingComplete", equalTo(false));
	}

	@Test
	void mixedCaseLegacyAccountCanStillLoginAndReceivesCanonicalSessionIdentity() {
		User user = User.builder().email("User@Example.com").password(passwordEncoder.encode("Password123!")).build();
		userRepository.saveAndFlush(user);

		Response response = login("user@example.com");

		response.then()
			.statusCode(200)
			.contentType(ContentType.JSON)
			.body("accessToken", not(nullValue()))
			.body("onboardingComplete", equalTo(false));
		assertNotNull(response.cookie("refresh_token"));
		assertEquals("User@Example.com", jwtService.extractEmail(response.jsonPath().getString("accessToken")));
		assertEquals(1, userRepository.count());
	}

	@Test
	void legacyCaseVariantDuplicatesAuthenticateDeterministicallyWithout500s() {
		userRepository.saveAndFlush(
				User.builder().email("User@Example.com").password(passwordEncoder.encode("Password123!")).build());
		userRepository.saveAndFlush(
				User.builder().email("user@example.com").password(passwordEncoder.encode("Password123!")).build());

		Response exactUppercase = login("User@Example.com");
		Response exactLowercase = login("user@example.com");
		Response fallbackCase = login("USER@example.com");

		exactUppercase.then().statusCode(200).body("onboardingComplete", equalTo(false));
		exactLowercase.then().statusCode(200).body("onboardingComplete", equalTo(false));
		fallbackCase.then().statusCode(200).body("onboardingComplete", equalTo(false));
		assertEquals("User@Example.com", jwtService.extractEmail(exactUppercase.jsonPath().getString("accessToken")));
		assertEquals("user@example.com", jwtService.extractEmail(exactLowercase.jsonPath().getString("accessToken")));
		assertEquals("User@Example.com", jwtService.extractEmail(fallbackCase.jsonPath().getString("accessToken")));
	}

	@Test
	void duplicateRegistrationReturnsStructuredFailureWithoutReplacingSession() {
		register("duplicate@example.com").then().statusCode(200);

		given().contentType(ContentType.JSON)
			.body(credentials("duplicate@example.com"))
			.when()
			.post("/api/auth/register")
			.then()
			.statusCode(400)
			.contentType(ContentType.JSON)
			.header(HttpHeaders.SET_COOKIE, nullValue())
			.body("error", equalTo("Bad Request"))
			.body("message", equalTo("Email already used!"));

		assertEquals(1, userRepository.count());
	}

	@Test
	void mixedCaseLegacyAccountRejectsCaseVariantRegistration() {
		User user = User.builder().email("User@Example.com").password(passwordEncoder.encode("Password123!")).build();
		userRepository.saveAndFlush(user);

		given().contentType(ContentType.JSON)
			.body(credentials("user@example.com"))
			.when()
			.post("/api/auth/register")
			.then()
			.statusCode(400)
			.contentType(ContentType.JSON)
			.header(HttpHeaders.SET_COOKIE, nullValue())
			.body("error", equalTo("Bad Request"))
			.body("message", equalTo("Email already used!"));

		assertEquals(1, userRepository.count());
	}

	@Test
	void authenticatedCompletionCreatesOneOwnedEventAndMarksUserComplete() {
		Response actorRegistration = register("actor@example.com");
		register("victim@example.com");
		String actorToken = actorRegistration.jsonPath().getString("accessToken");
		String victimId = userRepository.findByEmail("victim@example.com").orElseThrow().getId().toString();

		Response completion = given().contentType(ContentType.JSON)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + actorToken)
			.body(Map.of("firstName", "Sophie", "partnerFirstName", "Marcus", "familyName", "Müller-Weber", "venue",
					"Eichenfürst", "date", "2030-06-15T00:00:00", "userId", victimId))
			.when()
			.post("/api/onboarding");

		completion.then()
			.statusCode(200)
			.body("onboardingComplete", equalTo(true))
			.body("event.firstNameCoupleOne", equalTo("Sophie"))
			.body("event.firstNameCoupleTwo", equalTo("Marcus"))
			.body("event.location", equalTo("Eichenfürst"));

		User actor = userRepository.findByEmail("actor@example.com").orElseThrow();
		User victim = userRepository.findByEmail("victim@example.com").orElseThrow();
		Event event = eventRepository.findAll().getFirst();
		assertNotNull(actor.getOnboardingCompletedAt());
		assertNull(victim.getOnboardingCompletedAt());
		assertEquals(actor.getId(), event.getUser().getId());
	}

	@Test
	void repeatCompletionIsIdempotent() {
		Response registration = register("repeat@example.com");
		String accessToken = registration.jsonPath().getString("accessToken");

		Response first = complete(accessToken);
		first.then().statusCode(200);
		var onboardingCompletedAtAfterFirst = userRepository.findByEmail("repeat@example.com")
			.orElseThrow()
			.getOnboardingCompletedAt();

		Response repeated = complete(accessToken);
		repeated.then()
			.statusCode(200)
			.body("onboardingComplete", equalTo(true))
			.body("event.id", equalTo(first.jsonPath().getString("event.id")));
		var onboardingCompletedAtAfterRepeat = userRepository.findByEmail("repeat@example.com")
			.orElseThrow()
			.getOnboardingCompletedAt();

		assertEquals(1, eventRepository.count());
		assertEquals(onboardingCompletedAtAfterFirst, onboardingCompletedAtAfterRepeat);
	}

	@Test
	void completedStateIsRestoredByRefreshAndUserEndpoint() {
		Response registration = register("complete@example.com");
		String accessToken = registration.jsonPath().getString("accessToken");
		complete(accessToken).then().statusCode(200);

		given().cookie("refresh_token", registration.cookie("refresh_token"))
			.contentType(ContentType.JSON)
			.when()
			.post("/api/auth/refresh")
			.then()
			.statusCode(200)
			.body("onboardingComplete", equalTo(true));

		given().header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
			.when()
			.get("/api/user")
			.then()
			.statusCode(200)
			.body("onboardingComplete", equalTo(true))
			.body("events.size()", equalTo(1));
	}

	@Test
	void unauthorizedOrInvalidCompletionDoesNotWriteAnything() {
		given().contentType(ContentType.JSON)
			.body(onboardingPayload())
			.when()
			.post("/api/onboarding")
			.then()
			.statusCode(401);
		assertEquals(0, eventRepository.count());

		String accessToken = register("invalid@example.com").jsonPath().getString("accessToken");
		given().contentType(ContentType.JSON)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
			.body(Map.of("firstName", "", "partnerFirstName", "Marcus", "familyName", "Müller", "venue", "Hall"))
			.when()
			.post("/api/onboarding")
			.then()
			.statusCode(400)
			.body("error", equalTo("Validation failed"));

		assertEquals(0, eventRepository.count());
		assertNull(userRepository.findByEmail("invalid@example.com").orElseThrow().getOnboardingCompletedAt());
	}

	@Test
	void persistenceFailureRollsBackCompletionAndEvent() {
		User user = User.builder()
			.email("rollback@example.com")
			.password(passwordEncoder.encode("Password123!"))
			.build();
		userRepository.saveAndFlush(user);
		String oversizedFamilyName = "x".repeat(300);

		assertThrows(RuntimeException.class,
				() -> onboardingService.complete(user.getEmail(), new OnboardingRequest("Sophie", "Marcus",
						oversizedFamilyName, "Venue", java.time.LocalDateTime.of(2030, 6, 15, 0, 0), null)));

		entityManager.clear();
		assertEquals(0, eventRepository.count());
		assertNull(userRepository.findByEmail(user.getEmail()).orElseThrow().getOnboardingCompletedAt());
	}

	private Response register(String email) {
		return given().contentType(ContentType.JSON).body(credentials(email)).when().post("/api/auth/register");
	}

	private Response login(String email) {
		return given().contentType(ContentType.JSON).body(credentials(email)).when().post("/api/auth/login");
	}

	private Response complete(String accessToken) {
		return given().contentType(ContentType.JSON)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
			.body(onboardingPayload())
			.when()
			.post("/api/onboarding");
	}

	private Map<String, String> credentials(String email) {
		return Map.of("email", email, "password", "Password123!");
	}

	private Map<String, String> onboardingPayload() {
		return Map.of("firstName", "Sophie", "partnerFirstName", "Marcus", "familyName", "Müller-Weber", "venue",
				"Eichenfürst", "date", "2030-06-15T00:00:00");
	}

}
