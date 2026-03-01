package com.domenicwalther.brautcloud.service;

import com.domenicwalther.brautcloud.exception.InvalidRefreshTokenException;
import com.domenicwalther.brautcloud.model.RefreshToken;
import com.domenicwalther.brautcloud.model.User;
import com.domenicwalther.brautcloud.repository.RefreshTokenRepository;
import com.domenicwalther.brautcloud.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class RefreshTokenService {

	private final RefreshTokenRepository refreshTokenRepository;

	private final UserRepository userRepository;

	public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, UserRepository userRepository) {
		this.refreshTokenRepository = refreshTokenRepository;
		this.userRepository = userRepository;
	}

	@Value("${jwt.token.refreshExpires}")
	private Duration refreshExpiration;

	public RefreshToken createRefreshToken(User user) {
		refreshTokenRepository.deleteByUser(user);

		RefreshToken token = new RefreshToken();
		token.setUser(user);
		token.setToken(UUID.randomUUID().toString());
		token.setExpiresAt(Instant.now().plus(refreshExpiration));

		return refreshTokenRepository.save(token);
	}

	public RefreshToken validateRefreshToken(String token) {
		RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
			.orElseThrow(() -> new InvalidRefreshTokenException("Refresh token not found"));

		if (refreshToken.getExpiresAt().isBefore(Instant.now())) {
			refreshTokenRepository.delete(refreshToken);
			throw new InvalidRefreshTokenException("Refresh token expired");
		}

		return refreshToken;
	}

	public void deleteByUser(User user) {
		refreshTokenRepository.deleteByUser(user);
	}

	public void deleteByToken(String token) {
		refreshTokenRepository.findByToken(token).ifPresent(t -> deleteByUser(t.getUser()));
	}

}
