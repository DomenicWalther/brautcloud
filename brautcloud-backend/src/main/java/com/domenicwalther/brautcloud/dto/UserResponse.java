package com.domenicwalther.brautcloud.dto;

import com.domenicwalther.brautcloud.model.User;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class UserResponse {

	private UUID id;

	private LocalDateTime createdAt;

	private String email;

	private boolean emailVerified;

	private boolean onboardingComplete;

	private List<EventResponse> events;

	public static UserResponse fromUser(User user, List<EventResponse> events) {
		UserResponse response = new UserResponse();
		response.setId(user.getId());
		response.setCreatedAt(user.getCreatedAt());
		response.setEmail(user.getEmail());
		response.setEmailVerified(user.isEmailVerified());
		response.setOnboardingComplete(user.getOnboardingCompletedAt() != null);
		response.setEvents(events);
		return response;
	}

}
