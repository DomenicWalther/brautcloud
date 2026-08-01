package com.domenicwalther.brautcloud.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "events")
public class Event {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne
	@JoinColumn(name = "user_id")
	private User user;

	private String eventName;

	private String lastName;

	private String firstNameCoupleOne;

	private String firstNameCoupleTwo;

	private String location;

	private LocalDateTime date;

	private String password;

	private String qrCode;

	private long viewCount;

	@Column(name = "deletion_requested", nullable = false)
	private boolean deletionRequested;

	@Enumerated(EnumType.STRING)
	@Column(name = "lifecycle_state", nullable = false, length = 32)
	@Builder.Default
	private EventLifecycleState lifecycleState = EventLifecycleState.ACTIVE;

	@Column(insertable = false, updatable = false)
	private LocalDateTime createdAt;

	@OneToMany(mappedBy = "event")
	private List<Image> images;

	public boolean isUploadAllowed() {
		return !deletionRequested && lifecycleState == EventLifecycleState.ACTIVE;
	}

	public boolean isDeletionStarted() {
		return deletionRequested || lifecycleState != null && lifecycleState != EventLifecycleState.ACTIVE;
	}

	public void requestDeletion() {
		deletionRequested = true;
		lifecycleState = EventLifecycleState.DELETE_REQUESTED;
	}

	public void markDeleting() {
		if (lifecycleState == EventLifecycleState.DELETE_REQUESTED) {
			lifecycleState = EventLifecycleState.DELETING;
		}
	}

}
