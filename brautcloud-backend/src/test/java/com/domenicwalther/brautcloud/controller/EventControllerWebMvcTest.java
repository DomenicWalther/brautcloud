package com.domenicwalther.brautcloud.controller;

import com.domenicwalther.brautcloud.dto.EventImageDTO;
import com.domenicwalther.brautcloud.dto.EventRequest;
import com.domenicwalther.brautcloud.dto.EventResponse;
import com.domenicwalther.brautcloud.dto.EventUpdateRequest;
import com.domenicwalther.brautcloud.dto.ImageUploadResponse;
import com.domenicwalther.brautcloud.dto.PublicEventResponse;
import com.domenicwalther.brautcloud.exception.GalleryPasswordRequiredException;
import com.domenicwalther.brautcloud.exception.ResourceNotFoundException;
import com.domenicwalther.brautcloud.service.CustomUserDetailsService;
import com.domenicwalther.brautcloud.service.EventService;
import com.domenicwalther.brautcloud.service.GuestSessionService;
import com.domenicwalther.brautcloud.service.ImageService;
import com.domenicwalther.brautcloud.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class EventControllerWebMvcTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private EventService eventService;

	@MockitoBean
	private ImageService imageService;

	@MockitoBean
	private GuestSessionService guestSessionService;

	@MockitoBean
	private JwtService jwtService;

	@MockitoBean
	private CustomUserDetailsService userDetailsService;

	@Test
	@WithMockUser(username = "owner@example.com")
	void getEventsUsesAuthenticatedIdentityAndSerializesResponse() throws Exception {
		UUID eventId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		when(eventService.getEventsByUserEmail("owner@example.com")).thenReturn(List.of(new EventResponse(eventId,
				"Wedding", "Berlin", LocalDateTime.of(2030, 6, 15, 14, 0), userId, "Alex", "Sam", 5L, 2L, false)));

		mockMvc.perform(get("/api/events"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(eventId.toString()))
			.andExpect(jsonPath("$[0].eventName").value("Wedding"))
			.andExpect(jsonPath("$[0].userId").value(userId.toString()))
			.andExpect(jsonPath("$[0].viewCount").value(5))
			.andExpect(jsonPath("$[0].guestCount").value(2));
	}

	@Test
	void publicEventDetailsDoNotRequireOwnerIdentity() throws Exception {
		UUID eventId = UUID.randomUUID();
		when(eventService.getPublicEvent(eventId)).thenReturn(new PublicEventResponse(eventId, "Wedding", "Berlin",
				LocalDateTime.of(2030, 6, 15, 0, 0), "Alex", "Sam", false));

		mockMvc.perform(get("/api/events/{eventId}/public", eventId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(eventId.toString()))
			.andExpect(jsonPath("$.eventName").value("Wedding"))
			.andExpect(jsonPath("$.firstNameCoupleOne").value("Alex"))
			.andExpect(jsonPath("$.userId").doesNotExist());
	}

	@Test
	void publicImagesAndViewUsePublicServiceMethods() throws Exception {
		UUID eventId = UUID.randomUUID();
		UUID imageId = UUID.randomUUID();
		UUID visitorId = UUID.randomUUID();
		when(eventService.getPublicEventImages(eventId, null, null))
			.thenReturn(List.of(new EventImageDTO(imageId, "https://files.test/photo")));

		mockMvc.perform(get("/api/events/{eventId}/public/images", eventId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(imageId.toString()));
		mockMvc
			.perform(post("/api/events/{eventId}/public/view", eventId).contentType(MediaType.APPLICATION_JSON)
				.content("{\"visitorId\":\"%s\"}".formatted(visitorId)))
			.andExpect(status().isOk());

		verify(eventService).registerPublicView(eventId, visitorId);
	}

	@Test
	void publicGuestUploadRoutesUseEventScopeAndGalleryPassword() throws Exception {
		UUID eventId = UUID.randomUUID();
		UUID imageId = UUID.randomUUID();
		when(guestSessionService.createToken()).thenReturn("guest-session-token");
		when(guestSessionService.createCookie("guest-session-token"))
			.thenReturn(ResponseCookie.from("brautcloud-guest-session", "guest-session-token").build());
		when(imageService.generatePublicPresignedUploadUrlsWithMetadata(eq(eventId), eq("secret"),
				argThat(request -> request.getFileNames().equals(List.of("guest.jpg"))
						&& request.getFileSizes().equals(List.of(5L))),
				eq("guest-session-token"), anyString()))
			.thenReturn(List.of(new ImageUploadResponse(imageId, "https://uploads.test/guest")));

		mockMvc.perform(post("/api/events/{eventId}/public/images/presigned-url", eventId)
			.header("X-Gallery-Password", "secret")
			.contentType(MediaType.APPLICATION_JSON)
			.content(
					"{\"eventId\":\"%s\",\"fileNames\":[\"guest.jpg\"],\"contentTypes\":[\"image/jpeg\"],\"fileSizes\":[5]}"
						.formatted(UUID.randomUUID())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].imageId").value(imageId.toString()));

		mockMvc
			.perform(
					post("/api/events/{eventId}/public/images/uploaded", eventId).header("X-Gallery-Password", "secret")
						.contentType(MediaType.APPLICATION_JSON)
						.content("[\"%s\"]".formatted(imageId)))
			.andExpect(status().isNoContent());

		verify(imageService).markPublicImagesAsUploaded(eventId, "secret", List.of(imageId), null);
	}

	@Test
	void publicDeleteUsesGalleryScopeAndGuestSessionCookie() throws Exception {
		UUID eventId = UUID.randomUUID();
		UUID imageId = UUID.randomUUID();

		mockMvc
			.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.delete("/api/events/{eventId}/public/images/{imageId}", eventId, imageId)
				.cookie(new jakarta.servlet.http.Cookie("brautcloud-guest-session", "guest-session-token")))
			.andExpect(status().isNoContent());

		verify(imageService).deletePublicImage(eventId, null, imageId, "guest-session-token");
	}

	@Test
	void publicImagesWithMissingPasswordOnProtectedGalleryReturns401() throws Exception {
		UUID eventId = UUID.randomUUID();
		when(eventService.getPublicEventImages(eventId, null, null))
			.thenThrow(new GalleryPasswordRequiredException("Gallery password required"));

		mockMvc.perform(get("/api/events/{eventId}/public/images", eventId))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("Gallery password required"));
	}

	@Test
	@WithMockUser(username = "owner@example.com")
	void updateEventUsesAuthenticatedIdentityAndReturnsUpdatedDetails() throws Exception {
		UUID eventId = UUID.randomUUID();
		EventUpdateRequest request = new EventUpdateRequest("Updated wedding", "Alex", "Sam", "Berlin",
				LocalDateTime.of(2030, 6, 15, 0, 0), null);
		EventResponse response = new EventResponse(eventId, "Updated wedding", "Berlin", request.date(),
				UUID.randomUUID(), "Alex", "Sam", 0L, 0L, false);
		when(eventService.updateEvent("owner@example.com", eventId, request)).thenReturn(response);

		mockMvc
			.perform(put("/api/events/{eventId}", eventId).contentType(MediaType.APPLICATION_JSON)
				.content(
						"""
								{"eventName":"Updated wedding","firstNameCoupleOne":"Alex","firstNameCoupleTwo":"Sam","location":"Berlin","date":"2030-06-15T00:00:00"}
								"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.eventName").value("Updated wedding"));

		verify(eventService).updateEvent("owner@example.com", eventId, request);
	}

	@Test
	@WithMockUser(username = "owner@example.com")
	void createEventUsesAuthenticatedIdentityInsteadOfPayloadOwner() throws Exception {
		UUID userId = UUID.randomUUID();

		mockMvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content("""
				{
				  "userId": "%s",
				  "eventName": "Wedding",
				  "location": "Berlin",
				  "date": "2030-06-15T14:00:00"
				}
				""".formatted(userId))).andExpect(status().isOk());

		verify(eventService).addEvent(eq("owner@example.com"),
				argThat((EventRequest request) -> request.getUserId().equals(userId)
						&& request.getEventName().equals("Wedding")
						&& request.getDate().equals(LocalDateTime.of(2030, 6, 15, 14, 0))));
	}

	@Test
	@WithMockUser
	void invalidUuidReturnsStructuredBadRequest() throws Exception {
		mockMvc.perform(get("/api/events/not-a-uuid/images"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("Invalid parameter type"))
			.andExpect(jsonPath("$.parameter").value("eventID"))
			.andExpect(jsonPath("$.message").value("Expected type UUID"));
	}

	@Test
	@WithMockUser
	void serviceNotFoundFailureIsMappedToHttp404() throws Exception {
		UUID eventId = UUID.randomUUID();
		when(eventService.getEventImages("user", eventId)).thenThrow(new ResourceNotFoundException("Event not found"));

		mockMvc.perform(get("/api/events/{eventId}/images", eventId))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error").value("Not Found"))
			.andExpect(jsonPath("$.message").value("Event not found"));
	}

	@Test
	@WithMockUser
	void imageResponsePreservesIdAndPresignedUrl() throws Exception {
		UUID eventId = UUID.randomUUID();
		UUID imageId = UUID.randomUUID();
		when(eventService.getEventImages("user", eventId))
			.thenReturn(List.of(new EventImageDTO(imageId, "https://files.test/photo")));

		mockMvc.perform(get("/api/events/{eventId}/images", eventId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(imageId.toString()))
			.andExpect(jsonPath("$[0].url").value("https://files.test/photo"));
	}

	@Test
	@WithMockUser(username = "owner@example.com")
	void downloadEventImagesStreamsZipWithAttachmentHeader() throws Exception {
		UUID eventId = UUID.randomUUID();
		StreamingResponseBody body = outputStream -> outputStream.write("zip-bytes".getBytes());
		when(eventService.streamEventImagesAsZip("owner@example.com", eventId)).thenReturn(body);

		mockMvc.perform(get("/api/events/{eventId}/images/download", eventId))
			.andExpect(status().isOk())
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
				.string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"event-photos.zip\""));
	}

	@Test
	@WithMockUser(username = "owner@example.com")
	void downloadEventImagesReturnsNoContentForEmptyGallery() throws Exception {
		UUID eventId = UUID.randomUUID();
		when(eventService.streamEventImagesAsZip("owner@example.com", eventId)).thenReturn(null);

		mockMvc.perform(get("/api/events/{eventId}/images/download", eventId)).andExpect(status().isNoContent());
	}

	@Test
	@WithMockUser(username = "owner@example.com")
	void deleteEventUsesAuthenticatedIdentity() throws Exception {
		UUID eventId = UUID.randomUUID();

		mockMvc
			.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/events/{eventId}",
					eventId))
			.andExpect(status().isOk());

		verify(eventService).deleteEvent("owner@example.com", eventId);
	}

	@Test
	@WithMockUser(username = "owner@example.com")
	void registerViewUsesAuthenticatedIdentityAndVisitorIdFromPayload() throws Exception {
		UUID eventId = UUID.randomUUID();
		UUID visitorId = UUID.randomUUID();

		mockMvc
			.perform(post("/api/events/{eventId}/view", eventId).contentType(MediaType.APPLICATION_JSON)
				.content("{\"visitorId\":\"%s\"}".formatted(visitorId)))
			.andExpect(status().isOk());

		verify(eventService).registerView("owner@example.com", eventId, visitorId);
	}

	@Test
	@WithMockUser
	void registerViewWithoutVisitorIdReturnsStructuredBadRequest() throws Exception {
		UUID eventId = UUID.randomUUID();

		mockMvc
			.perform(post("/api/events/{eventId}/view", eventId).contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("Validation failed"));
	}

}
