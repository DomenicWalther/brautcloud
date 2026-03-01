package com.domenicwalther.brautcloud.dto;

import com.domenicwalther.brautcloud.model.User;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserResponse {

	private Long id;

	private String lastName;

	private String firstNameCoupleOne;

	private String firstNameCoupleTwo;

	private LocalDateTime createdAt;

	private String email;

	private boolean emailVerified;

	private List<EventResponse> events;

	public static UserResponse fromUser(User user) {
		UserResponse response = new UserResponse();
		response.setId(user.getId());
		response.setLastName(user.getLastName());
		response.setFirstNameCoupleOne(user.getFirstNameCoupleOne());
		response.setFirstNameCoupleTwo(user.getFirstNameCoupleTwo());
		response.setCreatedAt(user.getCreatedAt());
		response.setEmail(user.getEmail());
		response.setEmailVerified(user.isEmailVerified());

		List<EventResponse> eventResponses = user.getEvents().stream().map(EventResponse::fromEvent).toList();
		response.setEvents(eventResponses);
		return response;
	}

}
