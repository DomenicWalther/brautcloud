package com.domenicwalther.brautcloud.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
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

	@OneToMany(mappedBy = "user")
	@Builder.Default
	private List<Event> events = new ArrayList<>();

}
