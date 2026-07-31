package com.domenicwalther.brautcloud.service;

import com.domenicwalther.brautcloud.dto.AuthResponse;
import com.domenicwalther.brautcloud.model.RefreshToken;
import com.domenicwalther.brautcloud.model.User;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class AuthSessionService {

	private final JwtService jwtService;

	private final RefreshTokenService refreshTokenService;

	@Value("${app.cookie.secure:true}")
	private boolean cookieSecure = true;

	@Value("${app.cookie.same-site:Strict}")
	private String cookieSameSite = "Strict";

	public AuthSessionService(JwtService jwtService, RefreshTokenService refreshTokenService) {
		this.jwtService = jwtService;
		this.refreshTokenService = refreshTokenService;
	}

	public AuthResponse issue(User user, HttpServletResponse response) {
		return issue(user, refreshTokenService.createRefreshToken(user), response);
	}

	public AuthResponse issue(User user, RefreshToken refreshToken, HttpServletResponse response) {
		String accessToken = jwtService.generateToken(user);
		ResponseCookie cookie = ResponseCookie.from("refresh_token", refreshToken.getToken())
			.httpOnly(true)
			.secure(cookieSecure)
			.sameSite(cookieSameSite)
			.path("/api/auth")
			.maxAge(Duration.ofDays(30))
			.build();
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

		return new AuthResponse(accessToken, user.getOnboardingCompletedAt() != null);
	}

	public ResponseCookie expiredRefreshCookie() {
		return ResponseCookie.from("refresh_token", "")
			.httpOnly(true)
			.secure(cookieSecure)
			.sameSite(cookieSameSite)
			.path("/api/auth")
			.maxAge(0)
			.build();
	}

}
