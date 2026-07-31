package com.domenicwalther.brautcloud.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@Table(name = "images")
public class Image {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne
	@JoinColumn(name = "event_id")
	private Event event;

	private String imageKey;

	/** MIME type expected by the signed PUT and verified before publication. */
	private String contentType;

	/** Expected size supplied at presign time, when client metadata is available. */
	private Long sizeBytes;

	/**
	 * SHA-256 hash of server-issued guest browser session token, null for owner uploads.
	 */
	private String guestSessionHash;

	private boolean isUploaded;

	private boolean isVisible;

	@Column(name = "deletion_requested", nullable = false)
	private boolean deletionRequested;

	@Column(insertable = false, updatable = false)
	private LocalDateTime createdAt;

}
