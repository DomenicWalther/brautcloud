package com.domenicwalther.brautcloud.service;

import com.domenicwalther.brautcloud.dto.EventRequest;
import com.domenicwalther.brautcloud.dto.EventResponse;
import com.domenicwalther.brautcloud.model.Event;
import com.domenicwalther.brautcloud.model.User;
import com.domenicwalther.brautcloud.repository.EventRepository;
import com.domenicwalther.brautcloud.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EventService {

	private final EventRepository eventRepository;

	private final UserRepository userRepository;

	public EventService(EventRepository eventRepository, UserRepository userRepository) {
		this.eventRepository = eventRepository;
		this.userRepository = userRepository;
	}

	public List<EventResponse> getEvents() {
		return eventRepository.findAll().stream().map(EventResponse::fromEvent).toList();
	}

	public void addEvent(EventRequest request) {
		User user = userRepository.findById(request.getUserId())
			.orElseThrow(() -> new RuntimeException("User not found"));
		Event event = new Event();
		event.setEventName(request.getEventName());
		event.setLocation(request.getLocation());
		event.setDate(request.getDate());
		event.setPassword(request.getPassword());
		event.setQrCode(request.getQrCode());
		event.setUser(user);
		eventRepository.save(event);
	}

	public void deleteEvent(Long eventID) {
		eventRepository.deleteById(eventID);
	}

}
