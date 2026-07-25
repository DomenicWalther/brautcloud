package com.domenicwalther.brautcloud;

import com.domenicwalther.brautcloud.model.Event;
import com.domenicwalther.brautcloud.model.User;
import com.domenicwalther.brautcloud.repository.EventGuestVisitRepository;
import com.domenicwalther.brautcloud.repository.EventRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EventViewTrackingIntegrationTest extends FullStackIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private EventRepository eventRepository;

	@Autowired
	private EventGuestVisitRepository eventGuestVisitRepository;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtService jwtService;

	@BeforeEach
	void cleanDatabase() {
		refreshTokenRepository.deleteAll();
		eventGuestVisitRepository.deleteAll();
		eventRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void repeatedViewsFromSameVisitorIncreaseViewsButKeepGuestsAtOne() throws Exception {
		User owner = persistUser("owner@example.com");
		String token = jwtService.generateToken(owner.getEmail());
		Event event = eventRepository.saveAndFlush(TestFixtures.event(owner, "Wedding"));
		UUID visitorId = UUID.randomUUID();

		mockMvc.perform(view(event.getId(), token, visitorId)).andExpect(status().isOk());
		mockMvc.perform(view(event.getId(), token, visitorId)).andExpect(status().isOk());

		mockMvc.perform(get("/api/events").header("Authorization", bearer(token)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].viewCount").value(2))
			.andExpect(jsonPath("$[0].guestCount").value(1));
	}

	@Test
	void viewsFromDistinctVisitorsIncreaseGuestCount() throws Exception {
		User owner = persistUser("owner@example.com");
		String token = jwtService.generateToken(owner.getEmail());
		Event event = eventRepository.saveAndFlush(TestFixtures.event(owner, "Wedding"));

		mockMvc.perform(view(event.getId(), token, UUID.randomUUID())).andExpect(status().isOk());
		mockMvc.perform(view(event.getId(), token, UUID.randomUUID())).andExpect(status().isOk());

		mockMvc.perform(get("/api/events").header("Authorization", bearer(token)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].viewCount").value(2))
			.andExpect(jsonPath("$[0].guestCount").value(2));
	}

	@Test
	void foreignUserCannotRegisterViewsForAnotherUsersEvent() throws Exception {
		User owner = persistUser("owner@example.com");
		User intruder = persistUser("intruder@example.com");
		Event event = eventRepository.saveAndFlush(TestFixtures.event(owner, "Wedding"));
		String intruderToken = jwtService.generateToken(intruder.getEmail());

		mockMvc.perform(view(event.getId(), intruderToken, UUID.randomUUID()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Event not found"));

		assertThat(eventRepository.findById(event.getId()).orElseThrow().getViewCount()).isZero();
		assertThat(eventGuestVisitRepository.countByEventId(event.getId())).isZero();
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder view(UUID eventId, String token,
			UUID visitorId) {
		return post("/api/events/{eventId}/view", eventId).header("Authorization", bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"visitorId\":\"%s\"}".formatted(visitorId));
	}

	private User persistUser(String email) {
		User user = TestFixtures.user(email);
		user.setPassword(passwordEncoder.encode("correct-password"));
		return userRepository.save(user);
	}

	private static String bearer(String token) {
		return "Bearer " + token;
	}

}
