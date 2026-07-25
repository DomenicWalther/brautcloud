package com.domenicwalther.brautcloud.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

	private static final String SECRET = "unit-test-signing-secret-that-is-at-least-thirty-two-bytes";

	private JwtService jwtService;

	@BeforeEach
	void setUp() {
		jwtService = serviceWith(SECRET, 60_000);
	}

	@Test
	void generatedTokenContainsSubjectAndIsValidForMatchingUser() {
		String token = jwtService.generateToken("owner@example.com");
		UserDetails user = User.withUsername("owner@example.com").password("ignored").roles("USER").build();

		assertThat(jwtService.extractEmail(token)).isEqualTo("owner@example.com");
		assertThat(jwtService.isTokenValid(token, user)).isTrue();
	}

	@Test
	void tokenIsInvalidForDifferentUser() {
		String token = jwtService.generateToken("owner@example.com");
		UserDetails otherUser = User.withUsername("other@example.com").password("ignored").roles("USER").build();

		assertThat(jwtService.isTokenValid(token, otherUser)).isFalse();
	}

	@Test
	void expiredTokenIsReportedAsInvalid() {
		JwtService immediatelyExpiringService = serviceWith(SECRET, -1_000);
		String token = immediatelyExpiringService.generateToken("owner@example.com");
		UserDetails user = User.withUsername("owner@example.com").password("ignored").roles("USER").build();

		assertThat(immediatelyExpiringService.isTokenValid(token, user)).isFalse();
	}

	@Test
	void tokenSignedWithAnotherSecretIsInvalid() {
		String foreignToken = serviceWith("another-test-signing-secret-that-is-long-enough-for-hmac", 60_000)
			.generateToken("owner@example.com");
		UserDetails user = User.withUsername("owner@example.com").password("ignored").roles("USER").build();

		assertThat(jwtService.isTokenValid(foreignToken, user)).isFalse();
	}

	private static JwtService serviceWith(String secret, long expiration) {
		JwtService service = new JwtService();
		ReflectionTestUtils.setField(service, "secret", secret);
		ReflectionTestUtils.setField(service, "expiration", expiration);
		return service;
	}

}
