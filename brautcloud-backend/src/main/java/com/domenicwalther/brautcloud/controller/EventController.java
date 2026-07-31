package com.domenicwalther.brautcloud.controller;

import com.domenicwalther.brautcloud.dto.EventImageDTO;
import com.domenicwalther.brautcloud.exception.BadRequestException;
import com.domenicwalther.brautcloud.dto.EventRequest;
import com.domenicwalther.brautcloud.dto.EventResponse;
import com.domenicwalther.brautcloud.dto.EventUpdateRequest;
import com.domenicwalther.brautcloud.dto.EventViewRequest;
import com.domenicwalther.brautcloud.dto.PublicEventResponse;
import com.domenicwalther.brautcloud.dto.ImageUploadRequest;
import com.domenicwalther.brautcloud.dto.ImageUploadResponse;
import com.domenicwalther.brautcloud.service.EventService;
import com.domenicwalther.brautcloud.service.ImageService;
import com.domenicwalther.brautcloud.service.GuestSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
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

	private final ImageService imageService;

	private final GuestSessionService guestSessionService;

	public EventController(EventService eventService, ImageService imageService,
			GuestSessionService guestSessionService) {
		this.eventService = eventService;
		this.imageService = imageService;
		this.guestSessionService = guestSessionService;
	}

	@GetMapping
	public List<EventResponse> getEvents(@AuthenticationPrincipal UserDetails authenticatedUser) {
		return eventService.getEventsByUserEmail(authenticatedUser.getUsername());
	}

	@GetMapping("/{eventID}/public")
	public PublicEventResponse getPublicEvent(@PathVariable UUID eventID) {
		return eventService.getPublicEvent(eventID);
	}

	@GetMapping("/{eventID}/public/images")
	public List<EventImageDTO> getPublicEventImages(@PathVariable UUID eventID,
			@RequestHeader(name = "X-Gallery-Password", required = false) String galleryPassword,
			@CookieValue(name = GuestSessionService.COOKIE_NAME, required = false) String guestSessionToken) {
		return eventService.getPublicEventImages(eventID, galleryPassword, guestSessionToken);
	}

	@PostMapping("/{eventID}/public/images/presigned-url")
	public ResponseEntity<List<ImageUploadResponse>> getPublicImagePresignedUrls(@PathVariable UUID eventID,
			@RequestHeader(name = "X-Gallery-Password", required = false) String galleryPassword,
			@CookieValue(name = GuestSessionService.COOKIE_NAME, required = false) String guestSessionToken,
			HttpServletRequest servletRequest, @RequestBody ImageUploadRequest request) {
		if (request == null) {
			throw new BadRequestException("Event and file names are required");
		}
		boolean issueNewSession = !GuestSessionService.isValidToken(guestSessionToken);
		String sessionToken = issueNewSession ? guestSessionService.createToken() : guestSessionToken;
		ResponseEntity.BodyBuilder response = ResponseEntity.ok();
		if (issueNewSession) {
			response.header(HttpHeaders.SET_COOKIE, guestSessionService.createCookie(sessionToken).toString());
		}
		String clientAddress = servletRequest.getRemoteAddr() == null ? "unknown" : servletRequest.getRemoteAddr();
		List<ImageUploadResponse> uploads = request.getContentTypes() == null && request.getFileSizes() == null
				? imageService.generatePublicPresignedUploadUrls(eventID, galleryPassword, request.getFileNames(),
						sessionToken, clientAddress)
				: imageService.generatePublicPresignedUploadUrlsWithMetadata(eventID, galleryPassword, request,
						sessionToken, clientAddress);
		return response.body(uploads);
	}

	@PostMapping("/{eventID}/public/images/uploaded")
	public ResponseEntity<Void> markPublicImagesAsUploaded(@PathVariable UUID eventID,
			@RequestHeader(name = "X-Gallery-Password", required = false) String galleryPassword,
			@CookieValue(name = GuestSessionService.COOKIE_NAME, required = false) String guestSessionToken,
			@RequestBody List<UUID> imageIds) {
		imageService.markPublicImagesAsUploaded(eventID, galleryPassword, imageIds, guestSessionToken);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/{eventID}/public/images/{imageID}")
	public ResponseEntity<Void> deletePublicImage(@PathVariable UUID eventID, @PathVariable UUID imageID,
			@RequestHeader(name = "X-Gallery-Password", required = false) String galleryPassword,
			@CookieValue(name = GuestSessionService.COOKIE_NAME, required = false) String guestSessionToken) {
		imageService.deletePublicImage(eventID, galleryPassword, imageID, guestSessionToken);
		return ResponseEntity.noContent().build();
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

	@PutMapping("/{eventID}")
	public EventResponse updateEvent(@AuthenticationPrincipal UserDetails authenticatedUser, @PathVariable UUID eventID,
			@Valid @RequestBody EventUpdateRequest request) {
		return eventService.updateEvent(authenticatedUser.getUsername(), eventID, request);
	}

	@DeleteMapping("{eventID}")
	public void deleteEvent(@AuthenticationPrincipal UserDetails authenticatedUser, @PathVariable UUID eventID) {
		eventService.deleteEvent(authenticatedUser.getUsername(), eventID);
	}

	@PostMapping("/{eventID}/public/view")
	public void registerPublicView(@PathVariable UUID eventID, @Valid @RequestBody EventViewRequest request) {
		eventService.registerPublicView(eventID, request.visitorId());
	}

	@PostMapping("/{eventID}/view")
	public void registerView(@AuthenticationPrincipal UserDetails authenticatedUser, @PathVariable UUID eventID,
			@Valid @RequestBody EventViewRequest request) {
		eventService.registerView(authenticatedUser.getUsername(), eventID, request.visitorId());
	}

}
