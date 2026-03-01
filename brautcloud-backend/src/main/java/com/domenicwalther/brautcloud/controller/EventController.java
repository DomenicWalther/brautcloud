package com.domenicwalther.brautcloud.controller;

import com.domenicwalther.brautcloud.dto.EventRequest;
import com.domenicwalther.brautcloud.dto.EventResponse;
import com.domenicwalther.brautcloud.service.EventService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/events")
public class EventController {

	private final EventService eventService;

	public EventController(EventService eventService) {
		this.eventService = eventService;
	}

	@GetMapping
	public List<EventResponse> getEvents() {
		return eventService.getEvents();
	}

	@PostMapping
	public void addEvent(@RequestBody EventRequest request) {
		eventService.addEvent(request);
	}

	@DeleteMapping("/events/{eventID}")
	public void deleteEvent(@PathVariable String eventID) {
		eventService.deleteEvent(Long.parseLong(eventID));
	}

}
