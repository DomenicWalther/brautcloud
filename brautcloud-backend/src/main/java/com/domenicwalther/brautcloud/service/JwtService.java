package com.domenicwalther.brautcloud.service;

import com.domenicwalther.brautcloud.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtService {

	@Value("${jwt.token.secret}")
	private String secret;

	@Value("${jwt.token.expires}")
	private long expiration;

	private SecretKey getSigningKey() {
		return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
	}

	public String generateToken(String email) {
		return generateToken(email, 0);
	}

	public String generateToken(User user) {
		return generateToken(user.getEmail(), user.getTokenVersion());
	}

	private String generateToken(String email, int tokenVersion) {
		return Jwts.builder()
			.subject(email)
			.claim("tokenVersion", tokenVersion)
			.issuedAt(new Date())
			.expiration(new Date(System.currentTimeMillis() + expiration))
			.signWith(getSigningKey())
			.compact();
	}

	public String extractEmail(String token) {
		return parseClaims(token).getSubject();
	}

	public boolean isTokenValid(String token, UserDetails userDetails) {
		return isTokenValid(token, userDetails, 0);
	}

	public boolean isTokenValid(String token, UserDetails userDetails, int tokenVersion) {
		try {
			final Claims claims = parseClaims(token);
			Integer claimedVersion = claims.get("tokenVersion", Integer.class);
			return claims.getSubject().equals(userDetails.getUsername()) && claimedVersion != null
					&& claimedVersion == tokenVersion && !isTokenExpired(claims);
		}
		catch (JwtException | IllegalArgumentException ex) {
			return false;
		}
	}

	public Instant extractExpiration(String token) {
		return parseClaims(token).getExpiration().toInstant();
	}

	private Claims parseClaims(String token) {
		return Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token).getPayload();
	}

	private boolean isTokenExpired(Claims claims) {
		return claims.getExpiration().before(new Date());
	}

}
