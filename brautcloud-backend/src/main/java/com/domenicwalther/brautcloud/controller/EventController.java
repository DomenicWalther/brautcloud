package com.domenicwalther.brautcloud.controller;

import com.domenicwalther.brautcloud.dto.EventImageDTO;
import com.domenicwalther.brautcloud.dto.EventRequest;
import com.domenicwalther.brautcloud.dto.EventResponse;
import com.domenicwalther.brautcloud.service.EventService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
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
	public ResponseEntity<Page<EventImageDTO>> getEventImages(@PathVariable UUID eventID,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return eventService.getEventImages(eventID, page, size);
	}

	@PostMapping
	public void addEvent(@RequestBody EventRequest request) {
		eventService.addEvent(request);
	}

	@DeleteMapping("/events/{eventID}")
	public void deleteEvent(@PathVariable UUID eventID) {
		eventService.deleteEvent(eventID);
	}

}
