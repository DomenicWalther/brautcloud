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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class GalleryAccessRateLimiter {

	private static final Logger log = LoggerFactory.getLogger(GalleryAccessRateLimiter.class);

	private final ConcurrentMap<String, FailureState> failures = new ConcurrentHashMap<>();

	@Value("${app.gallery.rate-limit.window:10m}")
	private Duration window = Duration.ofMinutes(10);

	@Value("${app.gallery.rate-limit.max-failures:5}")
	private int maxFailures = 5;

	@Value("${app.gallery.rate-limit.initial-backoff:500ms}")
	private Duration initialBackoff = Duration.ofMillis(500);

	@Value("${app.gallery.rate-limit.max-backoff:15m}")
	private Duration maxBackoff = Duration.ofMinutes(15);

	@Value("${app.gallery.rate-limit.max-tracked-keys:10000}")
	private int maxTrackedKeys = 10000;

	public void check(UUID eventId, String clientAddress) {
		Instant now = Instant.now();
		checkScope("event:" + eventId, now);
		checkScope("client:" + normalizeClient(clientAddress), now);
	}

	public void recordFailure(UUID eventId, String clientAddress) {
		Instant now = Instant.now();
		recordFailure("event:" + eventId, now);
		recordFailure("client:" + normalizeClient(clientAddress), now);
		log.info("Gallery password failure event={} client={}", fingerprint(eventId.toString()),
				fingerprint(normalizeClient(clientAddress)));
	}

	public void recordSuccess(UUID eventId, String clientAddress) {
		clear("event:" + eventId);
		clear("client:" + normalizeClient(clientAddress));
	}

	private void checkScope(String scope, Instant now) {
		FailureState state = failures.get(scope);
		if (state == null) {
			return;
		}
		synchronized (state) {
			prune(state, now);
			if (state.blockedUntil != null && now.isBefore(state.blockedUntil)) {
				log.warn("Gallery password rate limit triggered scope={}", fingerprint(scope));
				throw new TooManyRequestsException("Too many gallery access attempts. Try again later.");
			}
			if (state.attempts.size() >= maxFailures) {
				throw new TooManyRequestsException("Too many gallery access attempts. Try again later.");
			}
		}
	}

	private void recordFailure(String scope, Instant now) {
		FailureState state = stateFor(scope);
		synchronized (state) {
			prune(state, now);
			state.attempts.addLast(now);
			state.consecutiveFailures++;
			if (state.consecutiveFailures >= 3) {
				long multiplier = 1L << Math.min(state.consecutiveFailures - 3, 20);
				long backoffMillis = Math.min(maxBackoff.toMillis(), initialBackoff.toMillis() * multiplier);
				state.blockedUntil = now.plusMillis(backoffMillis);
			}
		}
	}

	private FailureState stateFor(String scope) {
		FailureState existing = failures.get(scope);
		if (existing != null) {
			return existing;
		}
		evictIdleStates();
		if (failures.size() >= maxTrackedKeys) {
			throw new TooManyRequestsException("Too many gallery access attempts. Try again later.");
		}
		return failures.computeIfAbsent(scope, ignored -> new FailureState());
	}

	private void evictIdleStates() {
		Instant now = Instant.now();
		failures.entrySet().removeIf(entry -> {
			FailureState state = entry.getValue();
			synchronized (state) {
				prune(state, now);
				return state.attempts.isEmpty() && (state.blockedUntil == null || !now.isBefore(state.blockedUntil));
			}
		});
	}

	private void clear(String scope) {
		FailureState state = failures.get(scope);
		if (state == null) {
			return;
		}
		synchronized (state) {
			state.attempts.clear();
			state.consecutiveFailures = 0;
			state.blockedUntil = null;
		}
	}

	private void prune(FailureState state, Instant now) {
		Instant cutoff = now.minus(window);
		while (!state.attempts.isEmpty() && state.attempts.peekFirst().isBefore(cutoff)) {
			state.attempts.removeFirst();
		}
	}

	private String normalizeClient(String clientAddress) {
		return clientAddress == null || clientAddress.isBlank() ? "unknown" : clientAddress.trim();
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

	private static final class FailureState {

		private final Deque<Instant> attempts = new ArrayDeque<>();

		private int consecutiveFailures;

		private Instant blockedUntil;

	}

}
