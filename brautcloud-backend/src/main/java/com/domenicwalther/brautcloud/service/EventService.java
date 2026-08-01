package com.domenicwalther.brautcloud.service;

import com.domenicwalther.brautcloud.dto.EventImageDTO;
import com.domenicwalther.brautcloud.dto.EventRequest;
import com.domenicwalther.brautcloud.dto.EventResponse;
import com.domenicwalther.brautcloud.dto.EventUpdateRequest;
import com.domenicwalther.brautcloud.dto.PublicEventResponse;
import com.domenicwalther.brautcloud.exception.GalleryPasswordRequiredException;
import com.domenicwalther.brautcloud.exception.ResourceNotFoundException;
import com.domenicwalther.brautcloud.model.Event;
import com.domenicwalther.brautcloud.model.EventGuestVisit;
import com.domenicwalther.brautcloud.model.Image;
import com.domenicwalther.brautcloud.model.User;
import com.domenicwalther.brautcloud.repository.EventGuestVisitRepository;
import com.domenicwalther.brautcloud.repository.EventRepository;
import com.domenicwalther.brautcloud.repository.ImageRepository;
import com.domenicwalther.brautcloud.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class EventService {

	@Autowired
	private S3Service s3Service;

	private final EventRepository eventRepository;

	private final UserRepository userRepository;

	private final ImageRepository imageRepository;

	private final ResourceOwnershipService resourceOwnershipService;

	private final EventGuestVisitRepository eventGuestVisitRepository;

	private final PasswordEncoder passwordEncoder;

	private final StorageDeletionService storageDeletionService;

	public EventService(EventRepository eventRepository, UserRepository userRepository, ImageRepository imageRepository,
			ResourceOwnershipService resourceOwnershipService, EventGuestVisitRepository eventGuestVisitRepository,
			PasswordEncoder passwordEncoder, StorageDeletionService storageDeletionService) {
		this.eventRepository = eventRepository;
		this.userRepository = userRepository;
		this.imageRepository = imageRepository;
		this.resourceOwnershipService = resourceOwnershipService;
		this.eventGuestVisitRepository = eventGuestVisitRepository;
		this.passwordEncoder = passwordEncoder;
		this.storageDeletionService = storageDeletionService;
	}

	public List<EventResponse> getEvents() {
		return eventRepository.findAll()
			.stream()
			.filter(event -> !event.isDeletionStarted())
			.map(EventResponse::fromEvent)
			.toList();
	}

	public List<EventResponse> getEventsByUserEmail(String email) {
		User user = userRepository.findByEmail(email)
			.orElseThrow(() -> new ResourceNotFoundException("User not found"));
		return eventRepository.findByUser(user)
			.stream()
			.filter(event -> !event.isDeletionStarted())
			.map(this::toEventResponse)
			.toList();
	}

	public EventResponse toEventResponse(Event event) {
		return EventResponse.fromEvent(event, eventGuestVisitRepository.countByEventId(event.getId()));
	}

	public PublicEventResponse getPublicEvent(UUID eventId) {
		return PublicEventResponse.fromEvent(requireAvailableEvent(eventId));
	}

	@Transactional
	public void registerView(String email, UUID eventId, UUID visitorId) {
		registerView(resourceOwnershipService.requireOwnedEvent(email, eventId), visitorId);
	}

	@Transactional
	public void registerPublicView(UUID eventId, UUID visitorId) {
		registerView(findEvent(eventId), visitorId);
	}

	private void registerView(Event event, UUID visitorId) {
		eventRepository.incrementViewCount(event.getId());
		if (!eventGuestVisitRepository.existsByEventIdAndVisitorId(event.getId(), visitorId)) {
			try {
				eventGuestVisitRepository.save(EventGuestVisit.builder().event(event).visitorId(visitorId).build());
			}
			catch (DataIntegrityViolationException ignored) {
				// A concurrent request for the same visitor already recorded the distinct
				// visit.
			}
		}
	}

	public void addEvent(String email, EventRequest request) {
		User user = findUserByEmail(email);
		Event event = Event.builder()
			.eventName(request.getEventName())
			.lastName(request.getLastName())
			.firstNameCoupleOne(request.getFirstNameCoupleOne())
			.firstNameCoupleTwo(request.getFirstNameCoupleTwo())
			.location(request.getLocation())
			.date(request.getDate())
			.password(hashGalleryPassword(request.getPassword()))
			.qrCode(request.getQrCode())
			.user(user)
			.build();
		eventRepository.save(event);
	}

	@Transactional
	public EventResponse updateEvent(String email, UUID eventId, EventUpdateRequest request) {
		Event event = resourceOwnershipService.requireOwnedEvent(email, eventId);
		event.setEventName(request.eventName().trim());
		event.setFirstNameCoupleOne(request.firstNameCoupleOne().trim());
		event.setFirstNameCoupleTwo(request.firstNameCoupleTwo().trim());
		event.setLocation(request.location().trim());
		event.setDate(request.date());
		if (request.password() == null) {
			// null → leave existing password untouched, but upgrade legacy plaintext.
			normalizeStoredGalleryPassword(event);
		}
		else if (request.password().isBlank()) {
			event.setPassword(null);
		}
		else {
			event.setPassword(passwordEncoder.encode(request.password()));
		}
		return toEventResponse(eventRepository.save(event));
	}

	public void deleteEvent(String email, UUID eventID) {
		Event event = resourceOwnershipService.requireOwnedEvent(email, eventID);
		storageDeletionService.requestEventDeletion(event);
		storageDeletionService.processEventDeletion(event.getId());
	}

	public List<EventImageDTO> getEventImages(String email, UUID eventID) {
		resourceOwnershipService.requireOwnedEvent(email, eventID);
		return getEventImages(eventID);
	}

	public Event requirePublicGalleryAccess(UUID eventID, String galleryPassword) {
		Event event = requireAvailableEvent(eventID);
		if (event.getPassword() != null && !event.getPassword().isBlank()
				&& !matchesGalleryPassword(event, galleryPassword)) {
			throw new GalleryPasswordRequiredException("Gallery password required");
		}
		return event;
	}

	public List<EventImageDTO> getPublicEventImages(UUID eventID, String galleryPassword) {
		return getPublicEventImages(eventID, galleryPassword, null);
	}

	public List<EventImageDTO> getPublicEventImages(UUID eventID, String galleryPassword, String guestSessionToken) {
		requirePublicGalleryAccess(eventID, galleryPassword);
		String guestSessionHash = GuestSessionService.isValidToken(guestSessionToken)
				? GuestSessionService.hash(guestSessionToken) : null;
		return getEventImages(eventID, false, guestSessionHash);
	}

	private List<EventImageDTO> getEventImages(UUID eventID) {
		return getEventImages(eventID, true, null);
	}

	private List<EventImageDTO> getEventImages(UUID eventID, boolean ownerView, String guestSessionHash) {
		List<Image> images = imageRepository.findByEventIdAndIsUploadedTrue(eventID);

		return images.stream().filter(image -> !image.isDeletionStarted()).map(image -> {
			String url = s3Service.getPresignedUrl(image.getImageKey());
			boolean canDelete = ownerView
					|| guestSessionHash != null && guestSessionHash.equals(image.getGuestSessionHash());
			return new EventImageDTO(image.getId(), url, canDelete);
		}).toList();
	}

	public StreamingResponseBody streamEventImagesAsZip(String email, UUID eventID) {
		resourceOwnershipService.requireOwnedEvent(email, eventID);
		List<Image> images = imageRepository.findByEventIdAndIsUploadedTrue(eventID)
			.stream()
			.filter(image -> !image.isDeletionStarted())
			.toList();
		if (images.isEmpty()) {
			return null;
		}

		return outputStream -> {
			try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
				for (Image image : images) {
					try (InputStream inputStream = s3Service.getObject(image.getImageKey())) {
						zipOutputStream.putNextEntry(new ZipEntry(image.getImageKey()));
						inputStream.transferTo(zipOutputStream);
						zipOutputStream.closeEntry();
					}
				}
			}
		};
	}

	private Event requireAvailableEvent(UUID eventId) {
		Event event = findEvent(eventId);
		if (event.isDeletionStarted()) {
			throw new ResourceNotFoundException("Event not found");
		}
		return event;
	}

	private String hashGalleryPassword(String password) {
		if (password == null || password.isBlank()) {
			return null;
		}
		return passwordEncoder.encode(password);
	}

	private boolean matchesGalleryPassword(Event event, String candidate) {
		if (candidate == null || candidate.isBlank()) {
			return false;
		}
		String stored = event.getPassword();
		boolean encodedMatch = false;
		try {
			encodedMatch = passwordEncoder.matches(candidate, stored);
		}
		catch (IllegalArgumentException ignored) {
			// Legacy plaintext and malformed values are handled below.
		}
		if (encodedMatch) {
			if (!isEncodedGalleryPassword(stored)) {
				event.setPassword(passwordEncoder.encode(candidate));
				eventRepository.save(event);
			}
			return true;
		}
		if (isEncodedGalleryPassword(stored)) {
			return false;
		}
		boolean legacyMatch = MessageDigest.isEqual(stored.getBytes(StandardCharsets.UTF_8),
				candidate.getBytes(StandardCharsets.UTF_8));
		if (legacyMatch) {
			event.setPassword(passwordEncoder.encode(candidate));
			eventRepository.save(event);
		}
		return legacyMatch;
	}

	private void normalizeStoredGalleryPassword(Event event) {
		String stored = event.getPassword();
		if (stored != null && !stored.isBlank() && !isEncodedGalleryPassword(stored)) {
			event.setPassword(passwordEncoder.encode(stored));
		}
	}

	static boolean isEncodedGalleryPassword(String password) {
		return password != null
				&& (password.startsWith("$2a$") || password.startsWith("$2b$") || password.startsWith("$2y$"));
	}

	private Event findEvent(UUID eventId) {
		return eventRepository.findById(eventId).orElseThrow(() -> new ResourceNotFoundException("Event not found"));
	}

	private User findUserByEmail(String email) {
		return userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User not found"));
	}

}
