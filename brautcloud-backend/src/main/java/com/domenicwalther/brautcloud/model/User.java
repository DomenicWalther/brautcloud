package com.domenicwalther.brautcloud.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "users")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(insertable = false, updatable = false)
	private LocalDateTime createdAt;

	@Email
	@Column(unique = true, nullable = false)
	private String email;

	private boolean emailVerified;

	private LocalDateTime onboardingCompletedAt;

	@Size(min = 8, message = "Password must be at least 8 characters long")
	@Column(nullable = false)
	private String password;

	@Builder.Default
	private String role = "ROLE_USER";

	@Column(nullable = false)
	@Builder.Default
	private int tokenVersion = 0;

	@Builder.Default
	@OneToMany(mappedBy = "user")
	private List<Event> events = new ArrayList<>();

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public boolean isEmailVerified() {
		return emailVerified;
	}

	public void setEmailVerified(boolean emailVerified) {
		this.emailVerified = emailVerified;
	}

	public LocalDateTime getOnboardingCompletedAt() {
		return onboardingCompletedAt;
	}

	public void setOnboardingCompletedAt(LocalDateTime onboardingCompletedAt) {
		this.onboardingCompletedAt = onboardingCompletedAt;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getRole() {
		return role;
	}

	public void setRole(String role) {
		this.role = role;
	}

	public int getTokenVersion() {
		return tokenVersion;
	}

	public void setTokenVersion(int tokenVersion) {
		this.tokenVersion = tokenVersion;
	}

	public List<Event> getEvents() {
		return events;
	}

	public void setEvents(List<Event> events) {
		this.events = events;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		User user = (User) o;
		return id != null && id.equals(user.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}

	@Override
	public String toString() {
		return "User{" + "id=" + id + ", createdAt=" + createdAt + ", email='" + email + '\'' + ", emailVerified="
				+ emailVerified + ", onboardingCompletedAt=" + onboardingCompletedAt + ", role='" + role + '\''
				+ ", tokenVersion=" + tokenVersion + '}';
	}

}
