package com.domenicwalther.brautcloud.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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

	@Enumerated(EnumType.STRING)
	@Column(name = "lifecycle_state", nullable = false, length = 32)
	@Builder.Default
	private ImageLifecycleState lifecycleState = ImageLifecycleState.PENDING;

	@Column(insertable = false, updatable = false)
	private LocalDateTime createdAt;

	public void markAvailable() {
		isUploaded = true;
		isVisible = true;
		lifecycleState = ImageLifecycleState.AVAILABLE;
	}

	public void requestDeletion() {
		deletionRequested = true;
		lifecycleState = ImageLifecycleState.DELETE_REQUESTED;
	}

	public void markDeletionRetrying() {
		deletionRequested = true;
		lifecycleState = ImageLifecycleState.DELETE_RETRYING;
	}

	public boolean isDeletionStarted() {
		return deletionRequested || lifecycleState == ImageLifecycleState.DELETE_REQUESTED
				|| lifecycleState == ImageLifecycleState.DELETE_RETRYING;
	}

}
