package com.domenicwalther.brautcloud.service;

import com.domenicwalther.brautcloud.dto.EventResponse;
import com.domenicwalther.brautcloud.dto.OnboardingRequest;
import com.domenicwalther.brautcloud.dto.OnboardingResponse;
import com.domenicwalther.brautcloud.exception.ResourceNotFoundException;
import com.domenicwalther.brautcloud.model.Event;
import com.domenicwalther.brautcloud.model.User;
import com.domenicwalther.brautcloud.repository.EventRepository;
import com.domenicwalther.brautcloud.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class OnboardingService {

	private final UserRepository userRepository;

	private final EventRepository eventRepository;

	public OnboardingService(UserRepository userRepository, EventRepository eventRepository) {
		this.userRepository = userRepository;
		this.eventRepository = eventRepository;
	}

	@Transactional
	public OnboardingResponse complete(String email, OnboardingRequest request) {
		User user = userRepository.findForUpdateByEmail(email)
			.orElseThrow(() -> new ResourceNotFoundException("User not found"));

		if (user.getOnboardingCompletedAt() != null) {
			return eventRepository.findFirstByUserOrderByCreatedAtAsc(user)
				.map(event -> new OnboardingResponse(true, EventResponse.fromEvent(event)))
				.orElseGet(() -> new OnboardingResponse(true, null));
		}

		Event event = Event.builder()
			.eventName(request.familyName().trim())
			.lastName(request.familyName().trim())
			.firstNameCoupleOne(request.firstName().trim())
			.firstNameCoupleTwo(request.partnerFirstName().trim())
			.location(request.venue().trim())
			.user(user)
			.build();

		user.setOnboardingCompletedAt(LocalDateTime.now());
		Event savedEvent = eventRepository.saveAndFlush(event);

		return new OnboardingResponse(true, EventResponse.fromEvent(savedEvent));
	}

}
