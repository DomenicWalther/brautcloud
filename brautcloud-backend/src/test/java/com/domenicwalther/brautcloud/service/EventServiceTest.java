package com.domenicwalther.brautcloud.service;

import com.domenicwalther.brautcloud.dto.EventRequest;
import com.domenicwalther.brautcloud.dto.EventResponse;
import com.domenicwalther.brautcloud.exception.ResourceNotFoundException;
import com.domenicwalther.brautcloud.model.Event;
import com.domenicwalther.brautcloud.model.EventGuestVisit;
import com.domenicwalther.brautcloud.model.Image;
import com.domenicwalther.brautcloud.model.User;
import com.domenicwalther.brautcloud.repository.EventGuestVisitRepository;
import com.domenicwalther.brautcloud.repository.EventRepository;
import com.domenicwalther.brautcloud.repository.ImageRepository;
import com.domenicwalther.brautcloud.repository.UserRepository;
import com.domenicwalther.brautcloud.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

	@Mock
	private EventRepository eventRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private ImageRepository imageRepository;

	@Mock
	private EventGuestVisitRepository eventGuestVisitRepository;

	@Mock
	private S3Service s3Service;

	private EventService eventService;

	@BeforeEach
	void setUp() {
		ResourceOwnershipService resourceOwnershipService = new ResourceOwnershipService(eventRepository,
				imageRepository);
		eventService = new EventService(eventRepository, userRepository, imageRepository, resourceOwnershipService,
				eventGuestVisitRepository);
		ReflectionTestUtils.setField(eventService, "s3Service", s3Service);
	}

	@Test
	void eventsAreScopedToRequestedUserAndMappedToResponses() {
		User user = TestFixtures.user("owner@example.com");
		user.setId(UUID.randomUUID());
		Event event = TestFixtures.event(user, "Wedding");
		event.setId(UUID.randomUUID());
		event.setViewCount(7L);
		when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
		when(eventRepository.findByUser(user)).thenReturn(List.of(event));
		when(eventGuestVisitRepository.countByEventId(event.getId())).thenReturn(2L);

		List<EventResponse> responses = eventService.getEventsByUserEmail(user.getEmail());

		assertThat(responses).singleElement().satisfies(response -> {
			assertThat(response.getId()).isEqualTo(event.getId());
			assertThat(response.getEventName()).isEqualTo("Wedding");
			assertThat(response.getUserId()).isEqualTo(user.getId());
			assertThat(response.getViewCount()).isEqualTo(7L);
			assertThat(response.getGuestCount()).isEqualTo(2L);
		});
	}

	@Test
	void registeringAViewIncrementsViewCountAndRecordsNewVisitor() {
		User user = TestFixtures.user("owner@example.com");
		Event event = TestFixtures.event(user, "Wedding");
		UUID eventId = UUID.randomUUID();
		event.setId(eventId);
		UUID visitorId = UUID.randomUUID();
		when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
		when(eventGuestVisitRepository.existsByEventIdAndVisitorId(eventId, visitorId)).thenReturn(false);

		eventService.registerView(user.getEmail(), eventId, visitorId);

		verify(eventRepository).incrementViewCount(eventId);
		ArgumentCaptor<EventGuestVisit> visit = ArgumentCaptor.forClass(EventGuestVisit.class);
		verify(eventGuestVisitRepository).save(visit.capture());
		assertThat(visit.getValue().getEvent()).isSameAs(event);
		assertThat(visit.getValue().getVisitorId()).isEqualTo(visitorId);
	}

	@Test
	void repeatedViewFromKnownVisitorIncrementsViewsWithoutDuplicatingVisit() {
		User user = TestFixtures.user("owner@example.com");
		Event event = TestFixtures.event(user, "Wedding");
		UUID eventId = UUID.randomUUID();
		event.setId(eventId);
		UUID visitorId = UUID.randomUUID();
		when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
		when(eventGuestVisitRepository.existsByEventIdAndVisitorId(eventId, visitorId)).thenReturn(true);

		eventService.registerView(user.getEmail(), eventId, visitorId);

		verify(eventRepository).incrementViewCount(eventId);
		verify(eventGuestVisitRepository, never()).save(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void concurrentDuplicateVisitInsertIsSwallowed() {
		User user = TestFixtures.user("owner@example.com");
		Event event = TestFixtures.event(user, "Wedding");
		UUID eventId = UUID.randomUUID();
		event.setId(eventId);
		UUID visitorId = UUID.randomUUID();
		when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
		when(eventGuestVisitRepository.existsByEventIdAndVisitorId(eventId, visitorId)).thenReturn(false);
		when(eventGuestVisitRepository.save(org.mockito.ArgumentMatchers.any()))
			.thenThrow(new DataIntegrityViolationException("duplicate visitor"));

		eventService.registerView(user.getEmail(), eventId, visitorId);

		verify(eventRepository).incrementViewCount(eventId);
	}

	@Test
	void viewRegistrationForForeignEventIsRejected() {
		User owner = TestFixtures.user("owner@example.com");
		Event event = TestFixtures.event(owner, "Wedding");
		UUID eventId = UUID.randomUUID();
		event.setId(eventId);
		when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

		assertThatThrownBy(() -> eventService.registerView("other@example.com", eventId, UUID.randomUUID()))
			.isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("Event not found");
		verify(eventRepository, never()).incrementViewCount(org.mockito.ArgumentMatchers.any());
		verify(eventGuestVisitRepository, never()).save(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void unknownUserCannotCreateEvent() {
		EventRequest request = request(UUID.randomUUID());
		when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> eventService.addEvent("missing@example.com", request))
			.isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("User not found");
		verify(eventRepository, never()).save(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void eventRequestIsPersistedWithAuthenticatedOwner() {
		User user = TestFixtures.user("owner@example.com");
		user.setId(UUID.randomUUID());
		EventRequest request = request(UUID.randomUUID());
		when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

		eventService.addEvent(user.getEmail(), request);

		ArgumentCaptor<Event> event = ArgumentCaptor.forClass(Event.class);
		verify(eventRepository).save(event.capture());
		assertThat(event.getValue()).satisfies(saved -> {
			assertThat(saved.getUser()).isSameAs(user);
			assertThat(saved.getEventName()).isEqualTo("Wedding");
			assertThat(saved.getLocation()).isEqualTo("Berlin");
			assertThat(saved.getFirstNameCoupleOne()).isEqualTo("Alex");
			assertThat(saved.getFirstNameCoupleTwo()).isEqualTo("Sam");
		});
	}

	@Test
	void uploadedImagesAreMappedToPresignedDownloadUrls() {
		User user = TestFixtures.user("owner@example.com");
		user.setId(UUID.randomUUID());
		UUID eventId = UUID.randomUUID();
		Event event = TestFixtures.event(user, "Wedding");
		event.setId(eventId);
		Image first = new Image();
		first.setId(UUID.randomUUID());
		first.setImageKey("first.jpg");
		Image second = new Image();
		second.setId(UUID.randomUUID());
		second.setImageKey("second.jpg");
		when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
		when(imageRepository.findByEventIdAndIsUploadedTrue(eventId)).thenReturn(List.of(first, second));
		when(s3Service.getPresignedUrl("first.jpg")).thenReturn("https://files.test/first");
		when(s3Service.getPresignedUrl("second.jpg")).thenReturn("https://files.test/second");

		assertThat(eventService.getEventImages(user.getEmail(), eventId)).extracting("id", "url")
			.containsExactly(tuple(first.getId(), "https://files.test/first"),
					tuple(second.getId(), "https://files.test/second"));
	}

	@Test
	void foreignUserCannotReadOrDeleteAnotherUsersEvent() {
		User owner = TestFixtures.user("owner@example.com");
		Event event = TestFixtures.event(owner, "Wedding");
		UUID eventId = UUID.randomUUID();
		event.setId(eventId);
		when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

		assertThatThrownBy(() -> eventService.getEventImages("other@example.com", eventId))
			.isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("Event not found");
		assertThatThrownBy(() -> eventService.deleteEvent("other@example.com", eventId))
			.isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("Event not found");
		verify(eventRepository, never()).deleteById(eventId);
	}

	@Test
	void streamEventImagesAsZipBundlesEachUploadedImage() throws Exception {
		User user = TestFixtures.user("owner@example.com");
		UUID eventId = UUID.randomUUID();
		Event event = TestFixtures.event(user, "Wedding");
		event.setId(eventId);
		Image first = new Image();
		first.setId(UUID.randomUUID());
		first.setImageKey("first.jpg");
		Image second = new Image();
		second.setId(UUID.randomUUID());
		second.setImageKey("second.jpg");
		when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
		when(imageRepository.findByEventIdAndIsUploadedTrue(eventId)).thenReturn(List.of(first, second));
		when(s3Service.getObjectBytes("first.jpg")).thenReturn("first-bytes".getBytes());
		when(s3Service.getObjectBytes("second.jpg")).thenReturn("second-bytes".getBytes());

		StreamingResponseBody body = eventService.streamEventImagesAsZip(user.getEmail(), eventId);
		assertThat(body).isNotNull();

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		body.writeTo(out);

		try (ZipInputStream zipIn = new ZipInputStream(new java.io.ByteArrayInputStream(out.toByteArray()))) {
			ZipEntry firstEntry = zipIn.getNextEntry();
			assertThat(firstEntry.getName()).isEqualTo("first.jpg");
			ZipEntry secondEntry = zipIn.getNextEntry();
			assertThat(secondEntry.getName()).isEqualTo("second.jpg");
			assertThat(zipIn.getNextEntry()).isNull();
		}
	}

	@Test
	void streamEventImagesAsZipReturnsNullForEmptyGallery() {
		User user = TestFixtures.user("owner@example.com");
		UUID eventId = UUID.randomUUID();
		Event event = TestFixtures.event(user, "Wedding");
		event.setId(eventId);
		when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
		when(imageRepository.findByEventIdAndIsUploadedTrue(eventId)).thenReturn(List.of());

		assertThat(eventService.streamEventImagesAsZip(user.getEmail(), eventId)).isNull();
	}

	private static EventRequest request(UUID userId) {
		EventRequest request = new EventRequest();
		request.setUserId(userId);
		request.setEventName("Wedding");
		request.setLastName("Cloud");
		request.setFirstNameCoupleOne("Alex");
		request.setFirstNameCoupleTwo("Sam");
		request.setLocation("Berlin");
		request.setDate(LocalDateTime.of(2030, 6, 15, 14, 0));
		request.setPassword("guest-secret");
		request.setQrCode("qr-code");
		return request;
	}

	private static org.assertj.core.groups.Tuple tuple(Object... values) {
		return org.assertj.core.groups.Tuple.tuple(values);
	}

}
