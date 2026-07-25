package com.domenicwalther.brautcloud.controller;

import com.domenicwalther.brautcloud.dto.EventImageDTO;
import com.domenicwalther.brautcloud.dto.EventRequest;
import com.domenicwalther.brautcloud.dto.EventResponse;
import com.domenicwalther.brautcloud.dto.EventViewRequest;
import com.domenicwalther.brautcloud.service.EventService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

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

	@GetMapping("/{eventID}/images/download")
	public ResponseEntity<StreamingResponseBody> downloadEventImages(
			@AuthenticationPrincipal UserDetails authenticatedUser, @PathVariable UUID eventID) {
		StreamingResponseBody body = eventService.streamEventImagesAsZip(authenticatedUser.getUsername(), eventID);
		if (body == null) {
			return ResponseEntity.noContent().build();
		}

		return ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"event-photos.zip\"")
			.contentType(MediaType.valueOf("application/zip"))
			.body(body);
	}

	@PostMapping
	public void addEvent(@AuthenticationPrincipal UserDetails authenticatedUser, @RequestBody EventRequest request) {
		eventService.addEvent(authenticatedUser.getUsername(), request);
	}

	@DeleteMapping("{eventID}")
	public void deleteEvent(@AuthenticationPrincipal UserDetails authenticatedUser, @PathVariable UUID eventID) {
		eventService.deleteEvent(authenticatedUser.getUsername(), eventID);
	}

	@PostMapping("/{eventID}/view")
	public void registerView(@AuthenticationPrincipal UserDetails authenticatedUser, @PathVariable UUID eventID,
			@Valid @RequestBody EventViewRequest request) {
		eventService.registerView(authenticatedUser.getUsername(), eventID, request.visitorId());
	}

}
