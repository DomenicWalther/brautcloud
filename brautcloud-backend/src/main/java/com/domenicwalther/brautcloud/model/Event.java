package com.domenicwalther.brautcloud.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
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

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public User getUser() {
		return user;
	}

	public void setUser(User user) {
		this.user = user;
	}

	public String getEventName() {
		return eventName;
	}

	public void setEventName(String eventName) {
		this.eventName = eventName;
	}

	public String getLastName() {
		return lastName;
	}

	public void setLastName(String lastName) {
		this.lastName = lastName;
	}

	public String getFirstNameCoupleOne() {
		return firstNameCoupleOne;
	}

	public void setFirstNameCoupleOne(String firstNameCoupleOne) {
		this.firstNameCoupleOne = firstNameCoupleOne;
	}

	public String getFirstNameCoupleTwo() {
		return firstNameCoupleTwo;
	}

	public void setFirstNameCoupleTwo(String firstNameCoupleTwo) {
		this.firstNameCoupleTwo = firstNameCoupleTwo;
	}

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public LocalDateTime getDate() {
		return date;
	}

	public void setDate(LocalDateTime date) {
		this.date = date;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getQrCode() {
		return qrCode;
	}

	public void setQrCode(String qrCode) {
		this.qrCode = qrCode;
	}

	public long getViewCount() {
		return viewCount;
	}

	public void setViewCount(long viewCount) {
		this.viewCount = viewCount;
	}

	public boolean isDeletionRequested() {
		return deletionRequested;
	}

	public void setDeletionRequested(boolean deletionRequested) {
		this.deletionRequested = deletionRequested;
	}

	public EventLifecycleState getLifecycleState() {
		return lifecycleState;
	}

	public void setLifecycleState(EventLifecycleState lifecycleState) {
		this.lifecycleState = lifecycleState;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public List<Image> getImages() {
		return images;
	}

	public void setImages(List<Image> images) {
		this.images = images;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		Event event = (Event) o;
		return id != null && id.equals(event.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}

	@Override
	public String toString() {
		return "Event{" + "id=" + id + ", eventName='" + eventName + '\'' + ", lastName='" + lastName + '\''
				+ ", firstNameCoupleOne='" + firstNameCoupleOne + '\'' + ", firstNameCoupleTwo='" + firstNameCoupleTwo
				+ '\'' + ", location='" + location + '\'' + ", date=" + date + ", qrCode='" + qrCode + '\''
				+ ", viewCount=" + viewCount + ", deletionRequested=" + deletionRequested + ", createdAt=" + createdAt
				+ '}';
	}

}
