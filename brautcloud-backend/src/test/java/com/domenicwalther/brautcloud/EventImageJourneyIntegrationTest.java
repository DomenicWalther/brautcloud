package com.domenicwalther.brautcloud;

import com.domenicwalther.brautcloud.model.Event;
import com.domenicwalther.brautcloud.model.Image;
import com.domenicwalther.brautcloud.model.User;
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
