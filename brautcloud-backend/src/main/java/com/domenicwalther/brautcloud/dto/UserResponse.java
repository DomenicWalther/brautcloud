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

	private List<EventResponse> events;

	public static UserResponse fromUser(User user) {
		UserResponse response = new UserResponse();
		response.setId(user.getId());
		response.setCreatedAt(user.getCreatedAt());
		response.setEmail(user.getEmail());
		response.setEmailVerified(user.isEmailVerified());

		List<EventResponse> eventResponses = user.getEvents().stream().map(EventResponse::fromEvent).toList();
		response.setEvents(eventResponses);
		return response;
	}

}
