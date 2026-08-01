package com.domenicwalther.brautcloud.service;

import com.domenicwalther.brautcloud.exception.TooManyRequestsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class AuthenticationRateLimiter {

	private static final Logger log = LoggerFactory.getLogger(AuthenticationRateLimiter.class);

	private final ConcurrentMap<String, AttemptState> attempts = new ConcurrentHashMap<>();

	@Value("${app.auth.rate-limit.window:15m}")
	private Duration window = Duration.ofMinutes(15);

	@Value("${app.auth.login.max-attempts:20}")
	private int loginMaxAttempts = 20;

	@Value("${app.auth.registration.max-attempts:20}")
	private int registrationMaxAttempts = 20;

	@Value("${app.auth.login.initial-backoff:500ms}")
	private Duration initialBackoff = Duration.ofMillis(500);

	@Value("${app.auth.login.max-backoff:15m}")
	private Duration maxBackoff = Duration.ofMinutes(15);

	@Value("${app.auth.rate-limit.max-tracked-keys:10000}")
	private int maxTrackedKeys = 10000;

	public void checkLogin(String clientAddress, String email) {
		checkAndRecord("login:client:" + normalize(clientAddress), loginMaxAttempts);
		checkAndRecord("login:account:" + normalize(email), loginMaxAttempts);
	}

	public void recordLoginFailure(String clientAddress, String email) {
		backoff("login:client:" + normalize(clientAddress));
		backoff("login:account:" + normalize(email));
		log.info("Authentication failure client={} account={}", fingerprint(normalize(clientAddress)),
				fingerprint(normalize(email)));
	}

	public void recordLoginSuccess(String clientAddress, String email) {
		clearBackoff("login:client:" + normalize(clientAddress));
		clearBackoff("login:account:" + normalize(email));
	}

	public void checkRegistration(String clientAddress, String email) {
		checkAndRecord("registration:client:" + normalize(clientAddress), registrationMaxAttempts);
		checkAndRecord("registration:account:" + normalize(email), registrationMaxAttempts);
	}

	private void checkAndRecord(String key, int maxAttempts) {
		AttemptState state = stateFor(key);
		Instant now = Instant.now();
		synchronized (state) {
			prune(state, now);
			if (state.blockedUntil != null && now.isBefore(state.blockedUntil)) {
				log.warn("Authentication rate limit triggered scope={}", fingerprint(key));
				throw rateLimited();
			}
			if (state.requests.size() >= maxAttempts) {
				log.warn("Authentication rate limit triggered scope={}", fingerprint(key));
				throw rateLimited();
			}
			state.requests.addLast(now);
		}
	}

	private void backoff(String key) {
		AttemptState state = stateFor(key);
		Instant now = Instant.now();
		synchronized (state) {
			prune(state, now);
			state.consecutiveFailures++;
			long multiplier = 1L << Math.min(state.consecutiveFailures - 1, 20);
			long backoffMillis = Math.min(maxBackoff.toMillis(), initialBackoff.toMillis() * multiplier);
			state.blockedUntil = now.plusMillis(backoffMillis);
		}
	}

	private void clearBackoff(String key) {
		AttemptState state = attempts.get(key);
		if (state == null) {
			return;
		}
		synchronized (state) {
			state.consecutiveFailures = 0;
			state.blockedUntil = null;
		}
	}

	private void prune(AttemptState state, Instant now) {
		Instant cutoff = now.minus(window);
		while (!state.requests.isEmpty() && state.requests.peekFirst().isBefore(cutoff)) {
			state.requests.removeFirst();
		}
	}

	private AttemptState stateFor(String key) {
		AttemptState existing = attempts.get(key);
		if (existing != null) {
			return existing;
		}
		evictIdleStates();
		if (attempts.size() >= maxTrackedKeys) {
			throw rateLimited();
		}
		return attempts.computeIfAbsent(key, ignored -> new AttemptState());
	}

	private void evictIdleStates() {
		Instant now = Instant.now();
		attempts.entrySet().removeIf(entry -> {
			AttemptState state = entry.getValue();
			synchronized (state) {
				prune(state, now);
				return state.requests.isEmpty() && (state.blockedUntil == null || !now.isBefore(state.blockedUntil));
			}
		});
	}

	private TooManyRequestsException rateLimited() {
		return new TooManyRequestsException("Too many requests. Try again later.");
	}

	private String normalize(String value) {
		return value == null || value.isBlank() ? "unknown" : value.trim().toLowerCase(java.util.Locale.ROOT);
	}

	private String fingerprint(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			return java.util.HexFormat.of().formatHex(digest, 0, 8);
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private static final class AttemptState {

		private final Deque<Instant> requests = new ArrayDeque<>();

		private int consecutiveFailures;

		private Instant blockedUntil;

	}

}
