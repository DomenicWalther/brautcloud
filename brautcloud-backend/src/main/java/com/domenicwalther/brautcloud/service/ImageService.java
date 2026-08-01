package com.domenicwalther.brautcloud.service;

import com.domenicwalther.brautcloud.dto.ImageUploadRequest;
import com.domenicwalther.brautcloud.dto.ImageUploadResponse;
import com.domenicwalther.brautcloud.exception.BadRequestException;
import com.domenicwalther.brautcloud.exception.ResourceNotFoundException;
import com.domenicwalther.brautcloud.exception.StorageLifecycleException;
import com.domenicwalther.brautcloud.exception.TooManyRequestsException;
import com.domenicwalther.brautcloud.model.Event;
import com.domenicwalther.brautcloud.model.Image;
import com.domenicwalther.brautcloud.model.ImageLifecycleState;
import com.domenicwalther.brautcloud.repository.EventRepository;
import com.domenicwalther.brautcloud.repository.ImageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Service
public class ImageService {

	private static final Logger log = LoggerFactory.getLogger(ImageService.class);

	private static final Duration PRESIGN_RATE_WINDOW = Duration.ofMinutes(10);

	private static final int MAX_PRESIGN_REQUESTS_PER_WINDOW = 10;

	private static final int CLEANUP_BATCH_SIZE = 100;

	@Autowired
	private S3Service s3Service;

	@Autowired
	private EventRepository eventRepository;

	private final ImageRepository imageRepository;

	private final ResourceOwnershipService resourceOwnershipService;

	private final EventService eventService;

	private final StorageDeletionService storageDeletionService;

	private final ConcurrentMap<String, Deque<Instant>> presignAttempts = new ConcurrentHashMap<>();

	public ImageService(ImageRepository imageRepository, ResourceOwnershipService resourceOwnershipService,
			EventService eventService, StorageDeletionService storageDeletionService) {
		this.imageRepository = imageRepository;
		this.resourceOwnershipService = resourceOwnershipService;
		this.eventService = eventService;
		this.storageDeletionService = storageDeletionService;
	}

	@Transactional
	public List<ImageUploadResponse> generatePresignedUploadUrls(String email, ImageUploadRequest request) {
		if (request == null || request.getEventId() == null) {
			throw new BadRequestException("Event and file names are required");
		}
		Event event = resourceOwnershipService.requireOwnedEvent(email, request.getEventId());
		return generatePresignedUploadUrls(event, request, null, email, false);
	}

	/**
	 * Legacy entry point retained to return validation errors for callers that omit byte
	 * lengths. All presign requests must use the metadata-bearing command.
	 */
	public List<ImageUploadResponse> generatePublicPresignedUploadUrls(UUID eventID, String galleryPassword,
			List<String> fileNames, String guestSessionToken) {
		return generatePublicPresignedUploadUrls(eventID, galleryPassword, fileNames, guestSessionToken, null);
	}

	@Transactional
	public List<ImageUploadResponse> generatePublicPresignedUploadUrls(UUID eventID, String galleryPassword,
			List<String> fileNames, String guestSessionToken, String clientAddress) {
		return generatePublicPresignedUploadUrlsWithMetadata(eventID, galleryPassword,
				new ImageUploadRequest(eventID, fileNames, null, null), guestSessionToken, clientAddress);
	}

	@Transactional
	public List<ImageUploadResponse> generatePublicPresignedUploadUrlsWithMetadata(UUID eventID, String galleryPassword,
			ImageUploadRequest request, String guestSessionToken, String clientAddress) {
		Event event = eventService.requirePublicGalleryAccess(eventID, galleryPassword);
		ImageUploadRequest scopedRequest = new ImageUploadRequest(eventID,
				request == null ? null : request.getFileNames(), request == null ? null : request.getContentTypes(),
				request == null ? null : request.getFileSizes());
		validateUploadRequest(scopedRequest);
		String guestSessionHash = requireGuestSessionHash(guestSessionToken);
		return generatePresignedUploadUrls(event, scopedRequest, guestSessionHash,
				"public:" + eventID + ":" + clientAddress, true);
	}

	private List<ImageUploadResponse> generatePresignedUploadUrls(Event event, ImageUploadRequest request,
			String guestSessionHash, String rateLimitKey, boolean publicUpload) {
		List<UploadSpec> specifications = validateUploadRequest(request);
		Event uploadEvent = lockEventForUpload(event);
		enforceRateLimit(rateLimitKey);
		enforceQuota(uploadEvent, specifications, guestSessionHash, publicUpload);
		List<Image> savedImages = new ArrayList<>();
		try {
			return specifications.stream().map(specification -> {
				String key = UUID.randomUUID() + "-" + sanitizeFileName(specification.fileName());
				String uploadUrl = s3Service.getPresignedPutUrl(key, specification.contentType(),
						specification.sizeBytes());
				if (uploadUrl == null || uploadUrl.isBlank()) {
					throw new IllegalStateException("Could not create upload URL");
				}

				Image image = new Image();
				image.setEvent(uploadEvent);
				image.setImageKey(key);
				image.setContentType(specification.contentType());
				image.setSizeBytes(specification.sizeBytes());
				image.setGuestSessionHash(guestSessionHash);
				image.setVisible(true);
				image.setUploaded(false);
				image.setLifecycleState(ImageLifecycleState.PENDING);
				Image saved = imageRepository.save(image);
				savedImages.add(saved);
				return new ImageUploadResponse(saved.getId(), uploadUrl, specification.contentType());
			}).collect(Collectors.toList());
		}
		catch (RuntimeException exception) {
			// Presigning happens before each row is saved. Roll back manually for callers
			// outside a proxy.
			if (!savedImages.isEmpty()) {
				imageRepository.deleteAll(savedImages);
			}
			throw exception;
		}
	}

	@Transactional(noRollbackFor = BadRequestException.class)
	public void markImagesAsUploaded(String email, List<UUID> imageIds) {
		validateImageIds(imageIds);
		List<Image> images = resourceOwnershipService.requireOwnedImages(email, imageIds);
		ensureEventsAllowUpload(images);
		markImagesAsUploaded(images);
	}

	@Transactional(noRollbackFor = BadRequestException.class)
	public void markPublicImagesAsUploaded(UUID eventID, String galleryPassword, List<UUID> imageIds,
			String guestSessionToken) {
		Event event = eventService.requirePublicGalleryAccess(eventID, galleryPassword);
		validateImageIds(imageIds);
		Event uploadEvent = lockEventForUpload(event);
		String guestSessionHash = requireGuestSessionHash(guestSessionToken);
		List<Image> images = imageRepository.findAllById(imageIds);
		if (guestSessionHash == null || images.size() != imageIds.size() || !uploadEvent.isUploadAllowed()
				|| images.stream()
					.anyMatch(image -> image.isDeletionStarted() || image.getEvent() == null
							|| !uploadEvent.getId().equals(image.getEvent().getId())
							|| !guestSessionHash.equals(image.getGuestSessionHash()))) {
			throw new ResourceNotFoundException("Image not found");
		}
		markImagesAsUploaded(images);
	}

	private void markImagesAsUploaded(List<Image> images) {
		if (images.stream().anyMatch(Image::isDeletionStarted)) {
			throw new ResourceNotFoundException("Image not found");
		}
		List<Image> pendingImages = images.stream().filter(image -> !image.isUploaded()).toList();
		List<Image> invalidImages = new ArrayList<>();
		for (Image image : pendingImages) {
			ImageVerificationResult result = s3Service.verifyUploadedImage(image.getImageKey(), image.getContentType(),
					image.getSizeBytes());
			if (result == ImageVerificationResult.TRANSIENT_FAILURE || result == null) {
				// Keep row and object reference. Scheduled pending-upload cleanup can
				// enqueue durable deletion after storage recovers.
				throw new StorageLifecycleException("Uploaded image verification is pending object storage", null);
			}
			if (result == ImageVerificationResult.INVALID || result == ImageVerificationResult.MISSING) {
				invalidImages.add(image);
			}
		}
		if (!invalidImages.isEmpty()) {
			discardUnverifiedImages(invalidImages);
			throw new BadRequestException("Uploaded image is missing or invalid");
		}
		images.forEach(Image::markAvailable);
		imageRepository.saveAll(images);
	}

	private void discardUnverifiedImages(List<Image> images) {
		for (Image image : images) {
			try {
				s3Service.deleteFile(image.getImageKey());
			}
			catch (RuntimeException ignored) {
				// Metadata is still removed so an unverified object can never be
				// published.
			}
		}
		imageRepository.deleteAll(images);
	}

	private Event lockEventForUpload(Event authorizedEvent) {
		Event lockedEvent = eventRepository == null ? authorizedEvent
				: eventRepository.findByIdForUpdate(authorizedEvent.getId())
					.orElseThrow(() -> new ResourceNotFoundException("Event not found"));
		if (!lockedEvent.isUploadAllowed()) {
			throw new ResourceNotFoundException("Event not found");
		}
		return lockedEvent;
	}

	private void ensureEventsAllowUpload(List<Image> images) {
		Set<UUID> eventIds = new HashSet<>();
		for (Image image : images) {
			Event event = image.getEvent();
			if (event == null || !eventIds.add(event.getId())) {
				continue;
			}
			lockEventForUpload(event);
		}
	}

	private List<UploadSpec> validateUploadRequest(ImageUploadRequest request) {
		if (request == null) {
			throw new BadRequestException("Event and file names are required");
		}
		List<String> fileNames = request.getFileNames();
		if (fileNames == null || fileNames.isEmpty() || fileNames.size() > ImageUploadPolicy.MAX_FILES_PER_REQUEST) {
			throw new BadRequestException("At least one valid file name is required");
		}
		if (request.getContentTypes() != null && request.getContentTypes().size() != fileNames.size()) {
			throw new BadRequestException("File metadata does not match file names");
		}
		if (request.getFileSizes() == null || request.getFileSizes().size() != fileNames.size()) {
			throw new BadRequestException("File sizes are required for every upload");
		}

		Set<String> normalizedNames = new HashSet<>();
		List<UploadSpec> specifications = new ArrayList<>();
		for (int index = 0; index < fileNames.size(); index++) {
			String fileName = fileNames.get(index);
			if (!isSafeFileName(fileName) || !normalizedNames.add(fileName.toLowerCase(Locale.ROOT))) {
				throw new BadRequestException("At least one valid file name is required");
			}
			String contentType = ImageUploadPolicy.contentTypeForFileName(fileName);
			if (contentType == null) {
				throw new BadRequestException("Only supported image file types are allowed");
			}
			if (request.getContentTypes() != null) {
				String requestedType = ImageUploadPolicy.normalizeContentType(request.getContentTypes().get(index));
				if (!contentType.equals(requestedType) || !ImageUploadPolicy.isAllowedContentType(requestedType)) {
					throw new BadRequestException("File content type does not match its name");
				}
			}
			Long sizeBytes = request.getFileSizes().get(index);
			if (sizeBytes == null || sizeBytes <= 0 || sizeBytes > ImageUploadPolicy.MAX_IMAGE_BYTES) {
				throw new BadRequestException("Image size exceeds the allowed limit");
			}
			specifications.add(new UploadSpec(fileName, contentType, sizeBytes));
		}
		return specifications;
	}

	private void enforceQuota(Event event, List<UploadSpec> specifications, String guestSessionHash,
			boolean publicUpload) {
		long eventCount = imageRepository.countByEventId(event.getId());
		if (eventCount + specifications.size() > ImageUploadPolicy.MAX_IMAGES_PER_EVENT) {
			throw new BadRequestException("Gallery image limit reached");
		}
		long requestedBytes = specifications.stream()
			.map(UploadSpec::sizeBytes)
			.filter(size -> size != null)
			.mapToLong(Long::longValue)
			.sum();
		if (imageRepository.sumSizeBytesByEventId(event.getId()) + requestedBytes > ImageUploadPolicy.MAX_EVENT_BYTES) {
			throw new BadRequestException("Gallery storage limit reached");
		}
		if (publicUpload) {
			long guestCount = imageRepository.countByEventIdAndGuestSessionHash(event.getId(), guestSessionHash);
			if (guestCount + specifications.size() > ImageUploadPolicy.MAX_IMAGES_PER_GUEST_SESSION) {
				throw new BadRequestException("Guest upload limit reached");
			}
			if (imageRepository.sumSizeBytesByEventIdAndGuestSessionHash(event.getId(), guestSessionHash)
					+ requestedBytes > ImageUploadPolicy.MAX_GUEST_SESSION_BYTES) {
				throw new BadRequestException("Guest storage limit reached");
			}
		}
	}

	private void enforceRateLimit(String key) {
		Instant now = Instant.now();
		Deque<Instant> attempts = presignAttempts.computeIfAbsent(key, ignored -> new ArrayDeque<>());
		synchronized (attempts) {
			Instant cutoff = now.minus(PRESIGN_RATE_WINDOW);
			while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) {
				attempts.removeFirst();
			}
			if (attempts.size() >= MAX_PRESIGN_REQUESTS_PER_WINDOW) {
				throw new TooManyRequestsException("Upload request limit reached");
			}
			attempts.addLast(now);
		}
	}

	private boolean isSafeFileName(String fileName) {
		if (fileName == null || fileName.isBlank() || fileName.length() > 255 || fileName.equals(".")
				|| fileName.equals("..") || fileName.contains("/") || fileName.contains("\\")) {
			return false;
		}
		return fileName.chars().noneMatch(character -> character == 0 || Character.isISOControl(character));
	}

	private String sanitizeFileName(String fileName) {
		return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
	}

	private String requireGuestSessionHash(String guestSessionToken) {
		if (!GuestSessionService.isValidToken(guestSessionToken)) {
			throw new ResourceNotFoundException("Image not found");
		}
		return GuestSessionService.hash(guestSessionToken);
	}

	private void validateImageIds(List<UUID> imageIds) {
		if (imageIds != null && imageIds.size() > ImageUploadPolicy.MAX_FILES_PER_REQUEST) {
			throw new BadRequestException("At most 100 image IDs may be confirmed at once");
		}
		if (imageIds == null || imageIds.isEmpty() || imageIds.stream().anyMatch(id -> id == null)
				|| imageIds.stream().distinct().count() != imageIds.size()) {
			throw new ResourceNotFoundException("Image not found");
		}
	}

	public void deleteImageByImageID(String email, UUID imageID) {
		Image image = resourceOwnershipService.requireOwnedImage(email, imageID);
		deleteImage(image);
	}

	public void deletePublicImage(UUID eventID, String galleryPassword, UUID imageID, String guestSessionToken) {
		Event event = eventService.requirePublicGalleryAccess(eventID, galleryPassword);
		Image image = imageRepository.findById(imageID)
			.orElseThrow(() -> new ResourceNotFoundException("Image not found"));
		String guestSessionHash = requireGuestSessionHash(guestSessionToken);
		if (image.getEvent() == null || !event.getId().equals(image.getEvent().getId())
				|| !guestSessionHash.equals(image.getGuestSessionHash())) {
			throw new ResourceNotFoundException("Image not found");
		}
		deleteImage(image);
	}

	private void deleteImage(Image image) {
		storageDeletionService.requestImageDeletion(image);
		storageDeletionService.processImageDeletion(image.getId());
	}

	@Scheduled(cron = "0 0 * * * *") // Every hour
	public void cleanupUnuploadedImages() {
		LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
		imageRepository
			.findByIsUploadedFalseAndDeletionRequestedFalseAndCreatedAtBeforeOrderByCreatedAtAscIdAsc(oneHourAgo,
					PageRequest.of(0, CLEANUP_BATCH_SIZE))
			.forEach(image -> {
				try {
					storageDeletionService.requestImageDeletion(image);
					storageDeletionService.processImageDeletion(image.getId());
				}
				catch (RuntimeException ex) {
					// Leave marker and outbox job for the retry/reconciliation worker.
					log.warn("Pending upload cleanup queued for image {}: {}", image.getId(), ex.getMessage());
				}
			});
	}

	private record UploadSpec(String fileName, String contentType, Long sizeBytes) {
	}

}
