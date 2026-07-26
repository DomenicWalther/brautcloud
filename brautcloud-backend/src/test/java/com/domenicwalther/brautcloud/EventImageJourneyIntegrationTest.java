package com.domenicwalther.brautcloud;

import com.domenicwalther.brautcloud.model.Event;
import com.domenicwalther.brautcloud.model.Image;
import com.domenicwalther.brautcloud.model.User;
import com.domenicwalther.brautcloud.repository.EventGuestVisitRepository;
import com.domenicwalther.brautcloud.repository.EventRepository;
import com.domenicwalther.brautcloud.repository.ImageRepository;
import com.domenicwalther.brautcloud.repository.RefreshTokenRepository;
import com.domenicwalther.brautcloud.repository.UserRepository;
import com.domenicwalther.brautcloud.service.JwtService;
import com.domenicwalther.brautcloud.support.FullStackIntegrationTest;
import com.domenicwalther.brautcloud.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EventImageJourneyIntegrationTest extends FullStackIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private EventRepository eventRepository;

	@Autowired
	private EventGuestVisitRepository eventGuestVisitRepository;

	@Autowired
	private ImageRepository imageRepository;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtService jwtService;

	@BeforeEach
	void cleanDatabaseAndExternalDouble() {
		refreshTokenRepository.deleteAll();
		eventGuestVisitRepository.deleteAll();
		imageRepository.deleteAll();
		eventRepository.deleteAll();
		userRepository.deleteAll();
		reset(s3Service);
	}

	@Test
	void ownerCanCreateEventAndCompleteImageLifecycleWithoutLiveS3() throws Exception {
		User owner = persistUser("owner@example.com");
		String token = jwtService.generateToken(owner.getEmail());

		mockMvc
			.perform(post("/api/events").header("Authorization", bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content(eventRequest(owner.getId())))
			.andExpect(status().isOk());
		Event event = eventRepository.findByUser(owner).getFirst();
		assertThat(event.getEventName()).isEqualTo("Wedding");

		mockMvc.perform(get("/api/events").header("Authorization", bearer(token)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(event.getId().toString()))
			.andExpect(jsonPath("$[0].eventName").value("Wedding"));

		when(s3Service.getPresignedPutUrl(anyString()))
			.thenAnswer(invocation -> "https://uploads.test/" + invocation.getArgument(0));
		mockMvc
			.perform(
					post("/api/image/presigned-url").header("Authorization", bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"eventId\":\"%s\",\"fileNames\":[\"ceremony.jpg\",\"party.jpg\"]}"
							.formatted(event.getId())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].uploadUrl").value(org.hamcrest.Matchers.startsWith("https://uploads.test/")));

		assertThat(imageRepository.findAll()).hasSize(2).allSatisfy(image -> {
			assertThat(image.isUploaded()).isFalse();
			assertThat(image.getEvent().getId()).isEqualTo(event.getId());
		});
		Image ceremony = imageRepository.findAll()
			.stream()
			.filter(image -> image.getImageKey().endsWith("-ceremony.jpg"))
			.findFirst()
			.orElseThrow();

		mockMvc.perform(get("/api/events/{id}/images", event.getId()).header("Authorization", bearer(token)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isEmpty());
		verify(s3Service, never()).getPresignedUrl(anyString());

		mockMvc
			.perform(post("/api/image/uploaded").header("Authorization", bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content("[\"%s\"]".formatted(ceremony.getId())))
			.andExpect(status().isNoContent());
		when(s3Service.getPresignedUrl(ceremony.getImageKey())).thenReturn("https://files.test/ceremony");
		mockMvc.perform(get("/api/events/{id}/images", event.getId()).header("Authorization", bearer(token)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(ceremony.getId().toString()))
			.andExpect(jsonPath("$[0].url").value("https://files.test/ceremony"));

		mockMvc.perform(delete("/api/image/{id}", ceremony.getId()).header("Authorization", bearer(token)))
			.andExpect(status().isNoContent());
		assertThat(imageRepository.findById(ceremony.getId())).isEmpty();
		verify(s3Service).deleteFile(ceremony.getImageKey());

		mockMvc.perform(delete("/api/events/{id}", event.getId()).header("Authorization", bearer(token)))
			.andExpect(status().isOk());
		assertThat(eventRepository.findById(event.getId())).isEmpty();
		assertThat(imageRepository.findAll()).isEmpty();
	}

	@Test
	void guestsCanLoadPublicGalleryAndTrackingWithoutOpeningOwnerEndpoints() throws Exception {
		User owner = persistUser("owner@example.com");
		Event event = eventRepository.saveAndFlush(TestFixtures.event(owner, "Wedding"));
		Image image = imageRepository.saveAndFlush(TestFixtures.image(event, "ceremony.jpg", true));
		when(s3Service.getPresignedUrl(image.getImageKey())).thenReturn("https://files.test/ceremony");
		UUID visitorId = UUID.randomUUID();

		mockMvc.perform(get("/api/events/{id}/public", event.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(event.getId().toString()))
			.andExpect(jsonPath("$.eventName").value("Wedding"))
			.andExpect(jsonPath("$.location").value("Berlin"))
			.andExpect(jsonPath("$.userId").doesNotExist());
		mockMvc.perform(get("/api/events/{id}/public/images", event.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(image.getId().toString()))
			.andExpect(jsonPath("$[0].url").value("https://files.test/ceremony"));
		mockMvc
			.perform(post("/api/events/{id}/public/view", event.getId()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"visitorId\":\"%s\"}".formatted(visitorId)))
			.andExpect(status().isOk());

		assertThat(eventRepository.findById(event.getId()).orElseThrow().getViewCount()).isEqualTo(1);
		assertThat(eventGuestVisitRepository.countByEventId(event.getId())).isEqualTo(1);
		mockMvc.perform(get("/api/events/{id}/images", event.getId())).andExpect(status().isUnauthorized());
		mockMvc
			.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.put("/api/events/{id}", event.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void ownerCanUpdateSupportedEventDetails() throws Exception {
		User owner = persistUser("owner@example.com");
		String token = jwtService.generateToken(owner.getEmail());
		Event event = eventRepository.saveAndFlush(TestFixtures.event(owner, "Wedding"));

		mockMvc
			.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.put("/api/events/{id}", event.getId())
				.header("Authorization", bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content(
						"""
								{"eventName":"Updated wedding","firstNameCoupleOne":"Sophie","firstNameCoupleTwo":"Marcus","location":"Munich","date":"2031-07-20T00:00:00"}
								"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.eventName").value("Updated wedding"))
			.andExpect(jsonPath("$.date").value("2031-07-20T00:00:00"));

		Event updated = eventRepository.findById(event.getId()).orElseThrow();
		assertThat(updated.getFirstNameCoupleOne()).isEqualTo("Sophie");
		assertThat(updated.getFirstNameCoupleTwo()).isEqualTo("Marcus");
		assertThat(updated.getLocation()).isEqualTo("Munich");
		assertThat(updated.getDate()).isEqualTo(java.time.LocalDateTime.of(2031, 7, 20, 0, 0));
	}

	@Test
	void eventListingIsScopedToAuthenticatedUser() throws Exception {
		User owner = persistUser("owner@example.com");
		User other = persistUser("other@example.com");
		eventRepository.save(TestFixtures.event(owner, "Owners wedding"));
		eventRepository.save(TestFixtures.event(other, "Other wedding"));

		mockMvc.perform(get("/api/events").header("Authorization", bearer(jwtService.generateToken(owner.getEmail()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].eventName").value("Owners wedding"));
	}

	@Test
	void eventCreationIgnoresForgedPayloadOwnerAndUsesAuthenticatedUser() throws Exception {
		User owner = persistUser("owner@example.com");
		User victim = persistUser("victim@example.com");
		String token = jwtService.generateToken(owner.getEmail());

		mockMvc
			.perform(post("/api/events").header("Authorization", bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content(eventRequest(victim.getId())))
			.andExpect(status().isOk());

		assertThat(eventRepository.findByUser(owner)).hasSize(1);
		assertThat(eventRepository.findByUser(victim)).isEmpty();
		assertThat(eventRepository.findAll()).singleElement().satisfies(event -> {
			assertThat(event.getUser().getId()).isEqualTo(owner.getId());
			assertThat(event.getUser().getId()).isNotEqualTo(victim.getId());
		});
	}

	@Test
	void foreignUserCannotReadOrMutateAnotherUsersEventsOrImages() throws Exception {
		User owner = persistUser("owner@example.com");
		User intruder = persistUser("intruder@example.com");
		String ownerToken = jwtService.generateToken(owner.getEmail());
		String intruderToken = jwtService.generateToken(intruder.getEmail());

		mockMvc
			.perform(post("/api/events").header("Authorization", bearer(ownerToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content(eventRequest(owner.getId())))
			.andExpect(status().isOk());
		Event event = eventRepository.findByUser(owner).getFirst();

		when(s3Service.getPresignedPutUrl(anyString()))
			.thenAnswer(invocation -> "https://uploads.test/" + invocation.getArgument(0));
		mockMvc
			.perform(post("/api/image/presigned-url").header("Authorization", bearer(ownerToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"eventId\":\"%s\",\"fileNames\":[\"ceremony.jpg\"]}".formatted(event.getId())))
			.andExpect(status().isOk());
		Image image = imageRepository.findAll().getFirst();

		mockMvc.perform(get("/api/events/{id}/images", event.getId()).header("Authorization", bearer(intruderToken)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Event not found"));
		mockMvc.perform(delete("/api/events/{id}", event.getId()).header("Authorization", bearer(intruderToken)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Event not found"));
		mockMvc
			.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.put("/api/events/{id}", event.getId())
				.header("Authorization", bearer(intruderToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content(
						"""
								{"eventName":"Stolen wedding","firstNameCoupleOne":"Intruder","firstNameCoupleTwo":"Name","location":"Unknown","date":"2031-07-20T00:00:00"}
								"""))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Event not found"));
		mockMvc
			.perform(post("/api/image/presigned-url").header("Authorization", bearer(intruderToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"eventId\":\"%s\",\"fileNames\":[\"stolen.jpg\"]}".formatted(event.getId())))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Event not found"));
		mockMvc
			.perform(post("/api/image/uploaded").header("Authorization", bearer(intruderToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content("[\"%s\"]".formatted(image.getId())))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Image not found"));
		mockMvc.perform(delete("/api/image/{id}", image.getId()).header("Authorization", bearer(intruderToken)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Image not found"));

		assertThat(eventRepository.findById(event.getId())).isPresent();
		assertThat(imageRepository.findById(image.getId())).isPresent();
		assertThat(imageRepository.findAll()).hasSize(1);
		verify(s3Service, never()).deleteFile(image.getImageKey());
	}

	@Test
	void unknownResourcesAndMalformedIdentifiersReturnClientErrorsWithoutCallingS3() throws Exception {
		User owner = persistUser("owner@example.com");
		String token = jwtService.generateToken(owner.getEmail());
		UUID missingId = UUID.randomUUID();

		mockMvc
			.perform(post("/api/image/presigned-url").header("Authorization", bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"eventId\":\"%s\",\"fileNames\":[\"photo.jpg\"]}".formatted(missingId)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Event not found"));
		mockMvc.perform(delete("/api/image/{id}", missingId).header("Authorization", bearer(token)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Image not found"));
		mockMvc.perform(get("/api/events/not-a-uuid/images").header("Authorization", bearer(token)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.parameter").value("eventID"));
		verify(s3Service, never()).getPresignedPutUrl(anyString());
		verify(s3Service, never()).deleteFile(anyString());
	}

	private User persistUser(String email) {
		User user = TestFixtures.user(email);
		user.setPassword(passwordEncoder.encode("correct-password"));
		return userRepository.save(user);
	}

	private static String bearer(String token) {
		return "Bearer " + token;
	}

	private static String eventRequest(UUID userId) {
		return """
				{
				  "userId": "%s",
				  "eventName": "Wedding",
				  "lastName": "Cloud",
				  "firstNameCoupleOne": "Alex",
				  "firstNameCoupleTwo": "Sam",
				  "location": "Berlin",
				  "date": "2030-06-15T14:00:00",
				  "password": "guest-secret",
				  "qrCode": "qr-code"
				}
				""".formatted(userId);
	}

}
