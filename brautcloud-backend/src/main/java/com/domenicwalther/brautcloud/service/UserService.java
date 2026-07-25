package com.domenicwalther.brautcloud.service;

import com.domenicwalther.brautcloud.dto.EventResponse;
import com.domenicwalther.brautcloud.dto.UserResponse;
import com.domenicwalther.brautcloud.exception.ResourceNotFoundException;
import com.domenicwalther.brautcloud.model.User;
import com.domenicwalther.brautcloud.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

	private final UserRepository userRepository;

	private final EventService eventService;

	public UserService(UserRepository userRepository, EventService eventService) {
		this.userRepository = userRepository;
		this.eventService = eventService;
	}

	public User findByEmail(String email) {
		return userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User not found"));
	}

	public UserResponse getUserResponse(String email) {
		User user = findByEmail(email);
		List<EventResponse> events = user.getEvents().stream().map(eventService::toEventResponse).toList();
		return UserResponse.fromUser(user, events);
	}

}
