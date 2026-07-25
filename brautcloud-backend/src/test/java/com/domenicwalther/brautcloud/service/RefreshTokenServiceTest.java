package com.domenicwalther.brautcloud.service;

import com.domenicwalther.brautcloud.exception.InvalidRefreshTokenException;
import com.domenicwalther.brautcloud.model.RefreshToken;
import com.domenicwalther.brautcloud.model.User;
import com.domenicwalther.brautcloud.repository.RefreshTokenRepository;
import com.domenicwalther.brautcloud.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

	@Mock
	private RefreshTokenRepository refreshTokenRepository;

	@Mock
	private UserRepository userRepository;

	private RefreshTokenService refreshTokenService;

	@BeforeEach
	void setUp() {
		refreshTokenService = new RefreshTokenService(refreshTokenRepository, userRepository);
		ReflectionTestUtils.setField(refreshTokenService, "refreshExpiration", Duration.ofDays(30));
	}

	@Test
	void creatingTokenReplacesExistingSessionForUser() {
		User user = User.builder().email("owner@example.com").password("password").build();
		Instant before = Instant.now().plus(Duration.ofDays(30));
		when(refreshTokenRepository.save(org.mockito.ArgumentMatchers.any(RefreshToken.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));

		RefreshToken created = refreshTokenService.createRefreshToken(user);

		assertThat(created.getUser()).isSameAs(user);
		assertThat(created.getToken()).isNotBlank();
		assertThat(created.getExpiresAt()).isBetween(before, Instant.now().plus(Duration.ofDays(30)));
		InOrder calls = inOrder(refreshTokenRepository);
		calls.verify(refreshTokenRepository).deleteByUser(user);
		calls.verify(refreshTokenRepository).save(created);
	}

	@Test
	void validTokenIsReturned() {
		RefreshToken token = tokenExpiringAt(Instant.parse("2100-01-01T00:00:00Z"));
		when(refreshTokenRepository.findByToken("valid-token")).thenReturn(Optional.of(token));

		assertThat(refreshTokenService.validateRefreshToken("valid-token")).isSameAs(token);
		verify(refreshTokenRepository, never()).delete(token);
	}

	@Test
	void expiredTokenIsDeletedAndRejected() {
		RefreshToken token = tokenExpiringAt(Instant.EPOCH);
		when(refreshTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(token));

		assertThatThrownBy(() -> refreshTokenService.validateRefreshToken("expired-token"))
			.isInstanceOf(InvalidRefreshTokenException.class)
			.hasMessage("Refresh token expired");
		verify(refreshTokenRepository).delete(token);
	}

	@Test
	void unknownTokenIsRejectedWithoutDeletingAnything() {
		when(refreshTokenRepository.findByToken("missing-token")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> refreshTokenService.validateRefreshToken("missing-token"))
			.isInstanceOf(InvalidRefreshTokenException.class)
			.hasMessage("Refresh token not found");
		verify(refreshTokenRepository, never()).delete(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void deletingByTokenRevokesTheUsersSession() {
		User user = User.builder().email("owner@example.com").password("password").build();
		RefreshToken token = tokenExpiringAt(Instant.parse("2100-01-01T00:00:00Z"));
		token.setUser(user);
		when(refreshTokenRepository.findByToken("token")).thenReturn(Optional.of(token));

		refreshTokenService.deleteByToken("token");

		verify(refreshTokenRepository).deleteByUser(user);
	}

	private static RefreshToken tokenExpiringAt(Instant expiration) {
		RefreshToken token = new RefreshToken();
		token.setToken(expiration.equals(Instant.EPOCH) ? "expired-token" : "valid-token");
		token.setExpiresAt(expiration);
		return token;
	}

}
