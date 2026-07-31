package com.domenicwalther.brautcloud.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Data
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@OneToOne
	@JoinColumn(name = "user_id")
	private User user;

	@Column(name = "token_hash", nullable = false, unique = true, length = 64)
	@ToString.Exclude
	private String tokenHash;

	@Transient
	@ToString.Exclude
	private String token;

	@Column(nullable = false)
	private Instant expiresAt;

	public void setToken(String token) {
		this.token = token;
		this.tokenHash = token == null ? null : hashToken(token);
	}

	@PrePersist
	@PreUpdate
	private void ensureTokenHash() {
		if (tokenHash == null && token != null) {
			tokenHash = hashToken(token);
		}
	}

	public static String hashToken(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

}
