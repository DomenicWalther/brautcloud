package com.domenicwalther.brautcloud.controller;

import com.domenicwalther.brautcloud.dto.EventImageDTO;
import com.domenicwalther.brautcloud.dto.EventRequest;
import com.domenicwalther.brautcloud.dto.EventResponse;
import com.domenicwalther.brautcloud.exception.ResourceNotFoundException;
import com.domenicwalther.brautcloud.service.CustomUserDetailsService;
import com.domenicwalther.brautcloud.service.EventService;
import com.domenicwalther.brautcloud.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
	private JwtService jwtService;

	@MockitoBean
	private CustomUserDetailsService userDetailsService;

	@Test
	@WithMockUser(username = "owner@example.com")
	void getEventsUsesAuthenticatedIdentityAndSerializesResponse() throws Exception {
		UUID eventId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		when(eventService.getEventsByUserEmail("owner@example.com")).thenReturn(List.of(new EventResponse(eventId,
				"Wedding", "Berlin", LocalDateTime.of(2030, 6, 15, 14, 0), userId, "Alex", "Sam")));

		mockMvc.perform(get("/api/events"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(eventId.toString()))
			.andExpect(jsonPath("$[0].eventName").value("Wedding"))
			.andExpect(jsonPath("$[0].userId").value(userId.toString()));
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
		when(eventService.getEventImages(eventId)).thenThrow(new ResourceNotFoundException("Event not found"));

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
		when(eventService.getEventImages(eventId))
			.thenReturn(List.of(new EventImageDTO(imageId, "https://files.test/photo")));

		mockMvc.perform(get("/api/events/{eventId}/images", eventId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(imageId.toString()))
			.andExpect(jsonPath("$[0].url").value("https://files.test/photo"));
	}

}
