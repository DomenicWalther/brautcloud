package com.domenicwalther.brautcloud.service;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class GuestSessionService {

	public static final String COOKIE_NAME = "brautcloud-guest-session";

	private static final SecureRandom RANDOM = new SecureRandom();

	public String createToken() {
		byte[] token = new byte[32];
		RANDOM.nextBytes(token);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
	}

	public ResponseCookie createCookie(String token) {
		return createCookie(token, false);
	}

	public ResponseCookie createCookie(String token, boolean secure) {
		return ResponseCookie.from(COOKIE_NAME, token)
			.httpOnly(true)
			.secure(secure)
			.sameSite("Lax")
			.path("/")
			.maxAge(Duration.ofDays(30))
			.build();
	}

	public static String hash(String token) {
		if (token == null || token.isBlank()) {
			return null;
		}
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

}
