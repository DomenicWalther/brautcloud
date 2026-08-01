package com.domenicwalther.brautcloud.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
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

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public Event getEvent() {
		return event;
	}

	public void setEvent(Event event) {
		this.event = event;
	}

	public String getImageKey() {
		return imageKey;
	}

	public void setImageKey(String imageKey) {
		this.imageKey = imageKey;
	}

	public String getContentType() {
		return contentType;
	}

	public void setContentType(String contentType) {
		this.contentType = contentType;
	}

	public Long getSizeBytes() {
		return sizeBytes;
	}

	public void setSizeBytes(Long sizeBytes) {
		this.sizeBytes = sizeBytes;
	}

	public String getGuestSessionHash() {
		return guestSessionHash;
	}

	public void setGuestSessionHash(String guestSessionHash) {
		this.guestSessionHash = guestSessionHash;
	}

	public boolean isUploaded() {
		return isUploaded;
	}

	public void setUploaded(boolean uploaded) {
		isUploaded = uploaded;
	}

	public boolean isVisible() {
		return isVisible;
	}

	public void setVisible(boolean visible) {
		isVisible = visible;
	}

	public boolean isDeletionRequested() {
		return deletionRequested;
	}

	public void setDeletionRequested(boolean deletionRequested) {
		this.deletionRequested = deletionRequested;
	}

	public ImageLifecycleState getLifecycleState() {
		return lifecycleState;
	}

	public void setLifecycleState(ImageLifecycleState lifecycleState) {
		this.lifecycleState = lifecycleState;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		Image image = (Image) o;
		return id != null && id.equals(image.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}

	@Override
	public String toString() {
		return "Image{" + "id=" + id + ", imageKey='" + imageKey + '\'' + ", contentType='" + contentType + '\''
				+ ", sizeBytes=" + sizeBytes + ", guestSessionHash='" + guestSessionHash + '\'' + ", isUploaded="
				+ isUploaded + ", isVisible=" + isVisible + ", deletionRequested=" + deletionRequested + ", createdAt="
				+ createdAt + '}';
	}

}
