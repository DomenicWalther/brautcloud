package com.domenicwalther.brautcloud.controller;

import com.domenicwalther.brautcloud.dto.EventImageDTO;
import com.domenicwalther.brautcloud.dto.EventRequest;
import com.domenicwalther.brautcloud.dto.EventResponse;
import com.domenicwalther.brautcloud.service.EventService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events")
public class EventController {

	private final EventService eventService;

	public EventController(EventService eventService) {
		this.eventService = eventService;
	}

	@GetMapping
	public List<EventResponse> getEvents() {
		String email = SecurityContextHolder.getContext().getAuthentication().getName();
		return eventService.getEventsByUserEmail(email);
	}

	@GetMapping("/{eventID}/images")
	public List<EventImageDTO> getEventImages(@PathVariable UUID eventID) {
		String email = SecurityContextHolder.getContext().getAuthentication().getName();
		return eventService.getEventImages(email, eventID);
	}

	@PostMapping
	public void addEvent(@RequestBody EventRequest request) {
		String email = SecurityContextHolder.getContext().getAuthentication().getName();
		eventService.addEvent(email, request);
	}

	@DeleteMapping("{eventID}")
	public void deleteEvent(@PathVariable UUID eventID) {
		String email = SecurityContextHolder.getContext().getAuthentication().getName();
		eventService.deleteEvent(email, eventID);
	}

}
