package com.domenicwalther.brautcloud.service;

import com.domenicwalther.brautcloud.exception.TooManyRequestsException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class AbuseRateLimiterTest {

	@Test
	void galleryFailuresBackoffPerEventAndClientAndSuccessResetsState() {
		GalleryAccessRateLimiter limiter = new GalleryAccessRateLimiter();
		ReflectionTestUtils.setField(limiter, "initialBackoff", Duration.ZERO);
		ReflectionTestUtils.setField(limiter, "maxBackoff", Duration.ZERO);
		ReflectionTestUtils.setField(limiter, "maxFailures", 1);
		UUID eventId = UUID.randomUUID();

		limiter.recordFailure(eventId, "client-a");
		assertThatThrownBy(() -> limiter.check(eventId, "client-a")).isInstanceOf(TooManyRequestsException.class)
			.hasMessage("Too many gallery access attempts. Try again later.");

		limiter.recordSuccess(eventId, "client-a");
		assertThatCode(() -> limiter.check(eventId, "client-a")).doesNotThrowAnyException();
		assertThatCode(() -> limiter.check(eventId, "client-b")).doesNotThrowAnyException();
	}

	@Test
	void authenticationLimitsClientAndAccountWithoutIncludingIdentifiersInMessage() {
		AuthenticationRateLimiter limiter = new AuthenticationRateLimiter();
		ReflectionTestUtils.setField(limiter, "loginMaxAttempts", 1);
		String email = "owner@example.com";

		limiter.checkLogin("192.0.2.1", email);
		assertThatThrownBy(() -> limiter.checkLogin("192.0.2.1", email)).isInstanceOf(TooManyRequestsException.class)
			.hasMessage("Too many requests. Try again later.")
			.hasMessageNotContaining(email)
			.hasMessageNotContaining("192.0.2.1");
	}

}
