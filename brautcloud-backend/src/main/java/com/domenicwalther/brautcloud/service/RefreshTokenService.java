package com.domenicwalther.brautcloud.service;

import com.domenicwalther.brautcloud.exception.InvalidRefreshTokenException;
import com.domenicwalther.brautcloud.model.RefreshToken;
import com.domenicwalther.brautcloud.model.User;
import com.domenicwalther.brautcloud.repository.RefreshTokenRepository;
import com.domenicwalther.brautcloud.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
public class RefreshTokenService {

	private static final SecureRandom RANDOM = new SecureRandom();

	private final RefreshTokenRepository refreshTokenRepository;

	private final UserRepository userRepository;

	public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, UserRepository userRepository) {
		this.refreshTokenRepository = refreshTokenRepository;
		this.userRepository = userRepository;
	}

	@Value("${jwt.token.refreshExpires}")
	private Duration refreshExpiration;

	@Transactional
	public RefreshToken createRefreshToken(User user) {
		// One refresh token is allowed per user. Lock user row before replacing the
		// existing token so concurrent login requests cannot both insert a session.
		User lockedUser = userRepository.findForUpdateByEmail(user.getEmail()).orElse(user);
		refreshTokenRepository.deleteByUser(lockedUser);

		RefreshToken token = new RefreshToken();
		token.setUser(lockedUser);
		token.setToken(generateTokenValue());
		token.setExpiresAt(Instant.now().plus(refreshExpiration));

		return refreshTokenRepository.save(token);
	}

	@Transactional
	public RefreshToken rotate(String token) {
		RefreshToken refreshToken = findTokenForUpdate(token);
		ensureNotExpired(refreshToken);

		refreshToken.setToken(generateTokenValue());
		refreshToken.setExpiresAt(Instant.now().plus(refreshExpiration));
		RefreshToken rotated = refreshTokenRepository.save(refreshToken);
		return rotated == null ? refreshToken : rotated;
	}

	@Transactional
	public RefreshToken validateRefreshToken(String token) {
		RefreshToken refreshToken = findToken(token);
		ensureNotExpired(refreshToken);
		return refreshToken;
	}

	@Transactional
	public void deleteByUser(User user) {
		refreshTokenRepository.deleteByUser(user);
	}

	@Transactional
	public boolean deleteByToken(String token) {
		return findTokenForUpdateOrLegacyLookup(token).map(refreshToken -> {
			User user = refreshToken.getUser();
			user.setTokenVersion(user.getTokenVersion() + 1);
			userRepository.save(user);
			refreshTokenRepository.deleteByUser(user);
			return true;
		}).orElse(false);
	}

	@Transactional
	public boolean revokeAccessTokens(String email) {
		return userRepository.findForUpdateByEmail(email).map(user -> {
			user.setTokenVersion(user.getTokenVersion() + 1);
			userRepository.save(user);
			return true;
		}).orElse(false);
	}

	private RefreshToken findToken(String token) {
		return refreshTokenRepository.findByToken(token)
			.orElseThrow(() -> new InvalidRefreshTokenException("Refresh token not found"));
	}

	private RefreshToken findTokenForUpdate(String token) {
		return findTokenForUpdateOrLegacyLookup(token)
			.orElseThrow(() -> new InvalidRefreshTokenException("Refresh token not found"));
	}

	private java.util.Optional<RefreshToken> findTokenForUpdateOrLegacyLookup(String token) {
		return refreshTokenRepository.findByTokenForUpdate(token).or(() -> refreshTokenRepository.findByToken(token));
	}

	private void ensureNotExpired(RefreshToken refreshToken) {
		if (refreshToken.getExpiresAt().isBefore(Instant.now())) {
			refreshTokenRepository.delete(refreshToken);
			throw new InvalidRefreshTokenException("Refresh token expired");
		}
	}

	private String generateTokenValue() {
		byte[] token = new byte[32];
		RANDOM.nextBytes(token);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
	}

}
