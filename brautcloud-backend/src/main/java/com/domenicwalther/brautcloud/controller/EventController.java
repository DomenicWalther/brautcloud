package com.domenicwalther.brautcloud.controller;

import com.domenicwalther.brautcloud.dto.EventImageDTO;
import com.domenicwalther.brautcloud.dto.EventRequest;
import com.domenicwalther.brautcloud.dto.EventResponse;
import com.domenicwalther.brautcloud.service.EventService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
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
	public List<EventResponse> getEvents(@AuthenticationPrincipal UserDetails authenticatedUser) {
		return eventService.getEventsByUserEmail(authenticatedUser.getUsername());
	}

	@GetMapping("/{eventID}/images")
	public List<EventImageDTO> getEventImages(@AuthenticationPrincipal UserDetails authenticatedUser,
			@PathVariable UUID eventID) {
		return eventService.getEventImages(authenticatedUser.getUsername(), eventID);
	}

	@PostMapping
	public void addEvent(@AuthenticationPrincipal UserDetails authenticatedUser, @RequestBody EventRequest request) {
		eventService.addEvent(authenticatedUser.getUsername(), request);
	}

	@DeleteMapping("{eventID}")
	public void deleteEvent(@AuthenticationPrincipal UserDetails authenticatedUser, @PathVariable UUID eventID) {
		eventService.deleteEvent(authenticatedUser.getUsername(), eventID);
	}

}
