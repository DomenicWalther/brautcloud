package com.domenicwalther.brautcloud.service;

import com.domenicwalther.brautcloud.dto.ImageUploadRequest;
import com.domenicwalther.brautcloud.dto.ImageUploadResponse;
import com.domenicwalther.brautcloud.exception.ResourceNotFoundException;
import com.domenicwalther.brautcloud.model.Event;
import com.domenicwalther.brautcloud.model.Image;
import com.domenicwalther.brautcloud.model.ImageLifecycleState;
import com.domenicwalther.brautcloud.model.User;
import com.domenicwalther.brautcloud.repository.EventRepository;
import com.domenicwalther.brautcloud.repository.ImageRepository;
import com.domenicwalther.brautcloud.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageServiceTest {

	private static final String GUEST_SESSION_TOKEN = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

	@Mock
	private ImageRepository imageRepository;

	@Mock
	private EventRepository eventRepository;

	@Mock
	private S3Service s3Service;

	@Mock
	private EventService eventService;

	@Mock
	private StorageDeletionService storageDeletionService;

	private ImageService imageService;

	@BeforeEach
	void setUp() {
		ResourceOwnershipService resourceOwnershipService = new ResourceOwnershipService(eventRepository,
				imageRepository);
		imageService = new ImageService(imageRepository, resourceOwnershipService, eventService,
				storageDeletionService);
		ReflectionTestUtils.setField(imageService, "s3Service", s3Service);
	}

	@Test
	void uploadUrlsCreatePendingImageRecordsForEachFile() {
		UUID eventId = UUID.randomUUID();
		User owner = TestFixtures.user("owner@example.com");
		Event event = TestFixtures.event(owner, "Wedding");
		event.setId(eventId);
		ImageUploadRequest request = new ImageUploadRequest(eventId, List.of("ceremony.jpg", "party.jpg"),
				List.of("image/jpeg", "image/jpeg"), List.of(4L, 4L));
		AtomicInteger sequence = new AtomicInteger();
		when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
		when(imageRepository.save(any(Image.class))).thenAnswer(invocation -> {
			Image image = invocation.getArgument(0);
			image.setId(new UUID(0, sequence.incrementAndGet()));
			return image;
		});
		when(s3Service.getPresignedPutUrl(anyString(), anyString(), anyLong()))
			.thenAnswer(invocation -> "https://uploads.test/" + invocation.getArgument(0));

		List<ImageUploadResponse> responses = imageService.generatePresignedUploadUrls(owner.getEmail(), request);

		assertThat(responses).hasSize(2).allSatisfy(response -> {
			assertThat(response.getImageId()).isNotNull();
			assertThat(response.getUploadUrl()).startsWith("https://uploads.test/");
		});
		ArgumentCaptor<Image> images = ArgumentCaptor.forClass(Image.class);
		verify(imageRepository, org.mockito.Mockito.times(2)).save(images.capture());
		assertThat(images.getAllValues()).allSatisfy(image -> {
			assertThat(image.getEvent()).isSameAs(event);
			assertThat(image.isVisible()).isTrue();
			assertThat(image.isUploaded()).isFalse();
			assertThat(image.getLifecycleState()).isEqualTo(ImageLifecycleState.PENDING);
		})
			.extracting(Image::getImageKey)
			.anyMatch(key -> key.endsWith("-ceremony.jpg"))
			.anyMatch(key -> key.endsWith("-party.jpg"));
	}

	@Test
	void unknownEventDoesNotCreateUploadMetadataOrCallS3() {
		UUID eventId = UUID.randomUUID();
		ImageUploadRequest request = new ImageUploadRequest(eventId, List.of("photo.jpg"));
		when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> imageService.generatePresignedUploadUrls("owner@example.com", request))
			.isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("Event not found");
		verify(imageRepository, never()).save(any());
		verify(s3Service, never()).getPresignedPutUrl(anyString(), anyString(), anyLong());
	}

	@Test
	void presigningIsRejectedOnceEventDeletionBegins() {
		UUID eventId = UUID.randomUUID();
		User owner = TestFixtures.user("owner@example.com");
		Event event = TestFixtures.event(owner, "Wedding");
		event.setId(eventId);
		event.requestDeletion();
		when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

		assertThatThrownBy(() -> imageService.generatePresignedUploadUrls(owner.getEmail(),
				new ImageUploadRequest(eventId, List.of("photo.jpg"), List.of("image/jpeg"), List.of(42L))))
			.isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("Event not found");
		verify(imageRepository, never()).save(any(Image.class));
		verify(s3Service, never()).getPresignedPutUrl(anyString(), anyString(), anyLong());
	}

	@Test
	void publicUploadUrlsUseEventLinkAccessAndCreatePendingImages() {
		UUID eventId = UUID.randomUUID();
		User owner = TestFixtures.user("owner@example.com");
		Event event = TestFixtures.event(owner, "Wedding");
		event.setId(eventId);
		when(eventService.requirePublicGalleryAccess(eventId, null)).thenReturn(event);
		when(imageRepository.save(any(Image.class))).thenAnswer(invocation -> {
			Image image = invocation.getArgument(0);
			image.setId(UUID.randomUUID());
			return image;
		});
		when(s3Service.getPresignedPutUrl(anyString(), anyString(), anyLong()))
			.thenReturn("https://uploads.test/photo");

		ImageUploadRequest request = new ImageUploadRequest(eventId, List.of("guest photo.jpg"), List.of("image/jpeg"),
				List.of(4L));
		List<ImageUploadResponse> responses = imageService.generatePublicPresignedUploadUrlsWithMetadata(eventId, null,
				request, GUEST_SESSION_TOKEN, null);

		assertThat(responses).singleElement().satisfies(response -> {
			assertThat(response.getImageId()).isNotNull();
			assertThat(response.getUploadUrl()).isEqualTo("https://uploads.test/photo");
		});
		ArgumentCaptor<Image> image = ArgumentCaptor.forClass(Image.class);
		verify(imageRepository).save(image.capture());
		assertThat(image.getValue().getEvent()).isSameAs(event);
		assertThat(image.getValue().getGuestSessionHash()).isEqualTo(GuestSessionService.hash(GUEST_SESSION_TOKEN));
		assertThat(image.getValue().isUploaded()).isFalse();
		ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
		verify(s3Service).getPresignedPutUrl(key.capture(), eq("image/jpeg"), eq(4L));
		assertThat(key.getValue()).endsWith("-guest_photo.jpg");
	}

	@Test
	void presignedPutBindsDeclaredImageTypeAndLength() {
		UUID eventId = UUID.randomUUID();
		User owner = TestFixtures.user("owner@example.com");
		Event event = TestFixtures.event(owner, "Wedding");
		event.setId(eventId);
		ImageUploadRequest request = new ImageUploadRequest(eventId, List.of("photo.jpg"), List.of("image/jpeg"),
				List.of(42L));
		when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
		when(imageRepository.save(any(Image.class))).thenAnswer(invocation -> {
			Image image = invocation.getArgument(0);
			image.setId(UUID.randomUUID());
			return image;
		});
		when(s3Service.getPresignedPutUrl(anyString(), eq("image/jpeg"), eq(42L)))
			.thenReturn("https://uploads.test/photo");

		List<ImageUploadResponse> responses = imageService.generatePresignedUploadUrls(owner.getEmail(), request);

		assertThat(responses).singleElement().satisfies(response -> {
			assertThat(response.getUploadUrl()).isEqualTo("https://uploads.test/photo");
			assertThat(response.getContentType()).isEqualTo("image/jpeg");
		});
		verify(s3Service).getPresignedPutUrl(anyString(), eq("image/jpeg"), eq(42L));
	}

	@Test
	void publicUploadRejectsOversizedMetadataBeforeCreatingRecords() {
		UUID eventId = UUID.randomUUID();
		when(eventService.requirePublicGalleryAccess(eventId, null)).thenReturn(new Event());

		ImageUploadRequest request = new ImageUploadRequest(eventId, List.of("photo.jpg"), List.of("image/jpeg"),
				List.of(ImageUploadPolicy.MAX_IMAGE_BYTES + 1));
		assertThatThrownBy(() -> imageService.generatePublicPresignedUploadUrlsWithMetadata(eventId, null, request,
				GUEST_SESSION_TOKEN, null))
			.isInstanceOf(com.domenicwalther.brautcloud.exception.BadRequestException.class);
		verify(imageRepository, never()).save(any());
		verify(s3Service, never()).getPresignedPutUrl(anyString(), anyString(), anyLong());
	}

	@Test
	void ownerPresignRejectsMissingByteSize() {
		UUID eventId = UUID.randomUUID();
		Event event = TestFixtures.event(TestFixtures.user("owner@example.com"), "Wedding");
		event.setId(eventId);
		when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
		ImageUploadRequest request = new ImageUploadRequest(eventId, List.of("photo.jpg"), List.of("image/jpeg"), null);

		assertThatThrownBy(() -> imageService.generatePresignedUploadUrls("owner@example.com", request))
			.isInstanceOf(com.domenicwalther.brautcloud.exception.BadRequestException.class)
			.hasMessage("File sizes are required for every upload");
		verify(imageRepository, never()).save(any());
		verify(s3Service, never()).getPresignedPutUrl(anyString(), anyString(), anyLong());
	}

	@Test
	void transientVerificationFailurePreservesPendingRowForRetryableCleanup() {
		UUID imageId = UUID.randomUUID();
		User owner = TestFixtures.user("owner@example.com");
		Event event = TestFixtures.event(owner, "Wedding");
		Image image = TestFixtures.image(event, "pending.jpg", false);
		image.setId(imageId);
		when(imageRepository.findAllById(List.of(imageId))).thenReturn(List.of(image));
		when(s3Service.verifyUploadedImage("pending.jpg", null, 4L))
			.thenReturn(ImageVerificationResult.TRANSIENT_FAILURE);

		assertThatThrownBy(() -> imageService.markImagesAsUploaded(owner.getEmail(), List.of(imageId)))
			.isInstanceOf(com.domenicwalther.brautcloud.exception.StorageLifecycleException.class)
			.hasMessage("Uploaded image verification is pending object storage");
		assertThat(image.isUploaded()).isFalse();
		verify(imageRepository, never()).deleteAll(any());
		verify(imageRepository, never()).saveAll(any());
		verify(storageDeletionService, never()).requestImageDeletion(any());
	}

	@Test
	void publicConfirmationDiscardsMissingObjectInsteadOfPublishingIt() {
		UUID eventId = UUID.randomUUID();
		UUID imageId = UUID.randomUUID();
		User owner = TestFixtures.user("owner@example.com");
		Event event = TestFixtures.event(owner, "Wedding");
		event.setId(eventId);
		Image image = TestFixtures.image(event, "guest.jpg", false);
		image.setId(imageId);
		image.setGuestSessionHash(GuestSessionService.hash(GUEST_SESSION_TOKEN));
		when(eventService.requirePublicGalleryAccess(eventId, null)).thenReturn(event);
		when(imageRepository.findAllById(List.of(imageId))).thenReturn(List.of(image));
		when(s3Service.verifyUploadedImage("guest.jpg", null, 4L)).thenReturn(ImageVerificationResult.INVALID);

		assertThatThrownBy(
				() -> imageService.markPublicImagesAsUploaded(eventId, null, List.of(imageId), GUEST_SESSION_TOKEN))
			.isInstanceOf(com.domenicwalther.brautcloud.exception.BadRequestException.class)
			.hasMessage("Uploaded image is missing or invalid");
		verify(imageRepository).deleteAll(List.of(image));
		verify(imageRepository, never()).saveAll(any());
	}

	@Test
	void publicConfirmationRejectsImageFromAnotherEvent() {
		UUID eventId = UUID.randomUUID();
		UUID imageId = UUID.randomUUID();
		User owner = TestFixtures.user("owner@example.com");
		Event event = TestFixtures.event(owner, "Wedding");
		event.setId(eventId);
		Image image = TestFixtures.image(TestFixtures.event(owner, "Other wedding"), "other.jpg", false);
		image.setId(imageId);
		when(eventService.requirePublicGalleryAccess(eventId, "secret")).thenReturn(event);
		when(imageRepository.findAllById(List.of(imageId))).thenReturn(List.of(image));
		image.setGuestSessionHash(GuestSessionService.hash(GUEST_SESSION_TOKEN));

		assertThatThrownBy(
				() -> imageService.markPublicImagesAsUploaded(eventId, "secret", List.of(imageId), GUEST_SESSION_TOKEN))
			.isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("Image not found");
		verify(imageRepository, never()).saveAll(any());
	}

	@Test
	void publicConfirmationMarksOnlyConfirmedImagesAsUploaded() {
		UUID eventId = UUID.randomUUID();
		UUID imageId = UUID.randomUUID();
		User owner = TestFixtures.user("owner@example.com");
		Event event = TestFixtures.event(owner, "Wedding");
		event.setId(eventId);
		Image image = TestFixtures.image(event, "guest.jpg", false);
		image.setId(imageId);
		when(eventService.requirePublicGalleryAccess(eventId, "secret")).thenReturn(event);
		when(imageRepository.findAllById(List.of(imageId))).thenReturn(List.of(image));
		image.setGuestSessionHash(GuestSessionService.hash(GUEST_SESSION_TOKEN));
		when(s3Service.verifyUploadedImage("guest.jpg", null, 4L)).thenReturn(ImageVerificationResult.VALID);

		imageService.markPublicImagesAsUploaded(eventId, "secret", List.of(imageId), GUEST_SESSION_TOKEN);

		assertThat(image.isUploaded()).isTrue();
		assertThat(image.getLifecycleState()).isEqualTo(ImageLifecycleState.AVAILABLE);
		verify(imageRepository).saveAll(List.of(image));
	}

	@Test
	void publicUploadRejectsInvalidFileNamesBeforeCreatingMetadata() {
		UUID eventId = UUID.randomUUID();
		when(eventService.requirePublicGalleryAccess(eventId, null)).thenReturn(new Event());

		assertThatThrownBy(
				() -> imageService.generatePublicPresignedUploadUrls(eventId, null, List.of(), GUEST_SESSION_TOKEN))
			.isInstanceOf(com.domenicwalther.brautcloud.exception.BadRequestException.class);
		verify(imageRepository, never()).save(any());
		verify(s3Service, never()).getPresignedPutUrl(anyString(), anyString(), anyLong());
	}

	@Test
	void markingUploadedOnlyUpdatesOwnedImagesFoundByRepository() {
		UUID firstId = UUID.randomUUID();
		UUID secondId = UUID.randomUUID();
		User owner = TestFixtures.user("owner@example.com");
		Event event = TestFixtures.event(owner, "Wedding");
		Image first = new Image();
		first.setId(firstId);
		first.setEvent(event);
		Image second = new Image();
		second.setId(secondId);
		second.setEvent(event);
		first.setImageKey("first.jpg");
		first.setSizeBytes(4L);
		second.setImageKey("second.jpg");
		second.setSizeBytes(4L);
		when(imageRepository.findAllById(List.of(firstId, secondId))).thenReturn(List.of(first, second));
		when(s3Service.verifyUploadedImage(anyString(), nullable(String.class), anyLong()))
			.thenReturn(ImageVerificationResult.VALID);

		imageService.markImagesAsUploaded(owner.getEmail(), List.of(firstId, secondId));

		assertThat(first.isUploaded()).isTrue();
		assertThat(second.isUploaded()).isTrue();
		verify(imageRepository).saveAll(List.of(first, second));
	}

	@Test
	void markingUploadedRejectsPayloadWhenAnyRequestedImageIsMissing() {
		UUID foundId = UUID.randomUUID();
		UUID missingId = UUID.randomUUID();
		User owner = TestFixtures.user("owner@example.com");
		Image found = TestFixtures.image(TestFixtures.event(owner, "Wedding"), "found.jpg", false);
		found.setId(foundId);
		List<UUID> requestedIds = List.of(foundId, missingId);
		when(imageRepository.findAllById(requestedIds)).thenReturn(List.of(found));

		assertThatThrownBy(() -> imageService.markImagesAsUploaded(owner.getEmail(), requestedIds))
			.isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("Image not found");
		verify(imageRepository, never()).saveAll(any());
	}

	@Test
	void markingUploadedRejectsPayloadWithDuplicateIds() {
		UUID imageId = UUID.randomUUID();
		User owner = TestFixtures.user("owner@example.com");
		List<UUID> duplicateIds = List.of(imageId, imageId);

		assertThatThrownBy(() -> imageService.markImagesAsUploaded(owner.getEmail(), duplicateIds))
			.isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("Image not found");
		verify(imageRepository, never()).saveAll(any());
	}

	@Test
	void confirmationRejectsMoreThanOneHundredImageIdsBeforeRepositoryAccess() {
		User owner = TestFixtures.user("owner@example.com");
		List<UUID> imageIds = java.util.stream.IntStream.range(0, 101).mapToObj(ignored -> UUID.randomUUID()).toList();

		assertThatThrownBy(() -> imageService.markImagesAsUploaded(owner.getEmail(), imageIds))
			.isInstanceOf(com.domenicwalther.brautcloud.exception.BadRequestException.class)
			.hasMessage("At most 100 image IDs may be confirmed at once");
		verify(imageRepository, never()).findAllById(any());
	}

	@Test
	void uploadRejectsMoreThanOneHundredFilesBeforePresigning() {
		UUID eventId = UUID.randomUUID();
		User owner = TestFixtures.user("owner@example.com");
		Event event = TestFixtures.event(owner, "Wedding");
		event.setId(eventId);
		when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
		ImageUploadRequest request = new ImageUploadRequest(eventId,
				java.util.stream.IntStream.range(0, 101).mapToObj(index -> "photo-" + index + ".jpg").toList());

		assertThatThrownBy(() -> imageService.generatePresignedUploadUrls(owner.getEmail(), request))
			.isInstanceOf(com.domenicwalther.brautcloud.exception.BadRequestException.class);
		verify(s3Service, never()).getPresignedPutUrl(anyString(), anyString(), anyLong());
		verify(imageRepository, never()).save(any());
	}

	@Test
	void deletingImageRemovesMetadataAndObject() {
		UUID imageId = UUID.randomUUID();
		User owner = TestFixtures.user("owner@example.com");
		Event event = TestFixtures.event(owner, "Wedding");
		Image image = new Image();
		image.setId(imageId);
		image.setEvent(event);
		image.setImageKey("event/photo.jpg");
		when(imageRepository.findById(imageId)).thenReturn(Optional.of(image));

		imageService.deleteImageByImageID(owner.getEmail(), imageId);

		verify(storageDeletionService).requestImageDeletion(image);
		verify(storageDeletionService).processImageDeletion(imageId);
	}

	@Test
	void guestCanDeleteOnlyImageFromTheirServerIssuedSession() {
		UUID eventId = UUID.randomUUID();
		UUID imageId = UUID.randomUUID();
		User owner = TestFixtures.user("owner@example.com");
		Event event = TestFixtures.event(owner, "Wedding");
		event.setId(eventId);
		Image image = TestFixtures.image(event, "guest.jpg", true);
		image.setId(imageId);
		image.setGuestSessionHash(GuestSessionService.hash(GUEST_SESSION_TOKEN));
		when(eventService.requirePublicGalleryAccess(eventId, null)).thenReturn(event);
		when(imageRepository.findById(imageId)).thenReturn(Optional.of(image));

		imageService.deletePublicImage(eventId, null, imageId, GUEST_SESSION_TOKEN);

		verify(storageDeletionService).requestImageDeletion(image);
		verify(storageDeletionService).processImageDeletion(imageId);
	}

	@Test
	void guestCannotDeleteAnotherSessionOrAnotherGalleryImage() {
		UUID eventId = UUID.randomUUID();
		UUID imageId = UUID.randomUUID();
		User owner = TestFixtures.user("owner@example.com");
		Event event = TestFixtures.event(owner, "Wedding");
		event.setId(eventId);
		Event otherEvent = TestFixtures.event(owner, "Other wedding");
		Image image = TestFixtures.image(otherEvent, "other.jpg", true);
		image.setId(imageId);
		image.setGuestSessionHash(GuestSessionService.hash("other-session-token"));
		when(eventService.requirePublicGalleryAccess(eventId, null)).thenReturn(event);
		when(imageRepository.findById(imageId)).thenReturn(Optional.of(image));

		assertThatThrownBy(() -> imageService.deletePublicImage(eventId, null, imageId, GUEST_SESSION_TOKEN))
			.isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("Image not found");
		verify(imageRepository, never()).deleteById(any());
		verify(storageDeletionService, never()).requestImageDeletion(any());
		verify(storageDeletionService, never()).processImageDeletion(any());
	}

	@Test
	void deletingUnknownImageDoesNotCallS3() {
		UUID imageId = UUID.randomUUID();
		when(imageRepository.findById(imageId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> imageService.deleteImageByImageID("owner@example.com", imageId))
			.isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("Image not found");
		verify(imageRepository, never()).deleteById(any());
		verify(storageDeletionService, never()).requestImageDeletion(any());
		verify(storageDeletionService, never()).processImageDeletion(any());
	}

	@Test
	void uploadConfirmationDatabaseFailureLeavesPendingImageForStorageCleanup() {
		UUID imageId = UUID.randomUUID();
		User owner = TestFixtures.user("owner@example.com");
		Event event = TestFixtures.event(owner, "Wedding");
		Image image = TestFixtures.image(event, "pending.jpg", false);
		image.setId(imageId);
		when(imageRepository.findAllById(List.of(imageId))).thenReturn(List.of(image));
		when(s3Service.verifyUploadedImage("pending.jpg", null, 4L)).thenReturn(ImageVerificationResult.VALID);
		doThrow(new IllegalStateException("database unavailable")).when(imageRepository).saveAll(List.of(image));

		assertThatThrownBy(() -> imageService.markImagesAsUploaded(owner.getEmail(), List.of(imageId)))
			.isInstanceOf(IllegalStateException.class);
		assertThat(image.isUploaded()).isTrue();
		verify(storageDeletionService, never()).requestImageDeletion(any());
	}

	@Test
	void pendingCleanupUsesStorageDeletionOutboxInsteadOfDroppingReference() {
		UUID imageId = UUID.randomUUID();
		Image image = TestFixtures.image(TestFixtures.event(TestFixtures.user("owner@example.com"), "Wedding"),
				"pending.jpg", false);
		image.setId(imageId);
		when(imageRepository.findByIsUploadedFalseAndDeletionRequestedFalseAndCreatedAtBeforeOrderByCreatedAtAscIdAsc(
				any(), any(Pageable.class)))
			.thenReturn(List.of(image));
		imageService.cleanupUnuploadedImages();

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(imageRepository)
			.findByIsUploadedFalseAndDeletionRequestedFalseAndCreatedAtBeforeOrderByCreatedAtAscIdAsc(any(),
					pageable.capture());
		assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
		verify(storageDeletionService).requestImageDeletion(image);
		verify(storageDeletionService).processImageDeletion(imageId);
		verify(imageRepository, never()).delete(image);
	}

	@Test
	void foreignUserCannotMutateAnotherUsersImages() {
		User owner = TestFixtures.user("owner@example.com");
		Event event = TestFixtures.event(owner, "Wedding");
		UUID eventId = UUID.randomUUID();
		event.setId(eventId);
		UUID imageId = UUID.randomUUID();
		Image image = new Image();
		image.setId(imageId);
		image.setEvent(event);
		image.setImageKey("event/photo.jpg");
		ImageUploadRequest request = new ImageUploadRequest(eventId, List.of("photo.jpg"));
		when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
		when(imageRepository.findAllById(List.of(imageId))).thenReturn(List.of(image));
		when(imageRepository.findById(imageId)).thenReturn(Optional.of(image));

		assertThatThrownBy(() -> imageService.generatePresignedUploadUrls("other@example.com", request))
			.isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("Event not found");
		assertThatThrownBy(() -> imageService.markImagesAsUploaded("other@example.com", List.of(imageId)))
			.isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("Image not found");
		assertThatThrownBy(() -> imageService.deleteImageByImageID("other@example.com", imageId))
			.isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("Image not found");
		verify(imageRepository, never()).save(any());
		verify(imageRepository, never()).saveAll(any());
		verify(imageRepository, never()).deleteById(any());
		verify(s3Service, never()).getPresignedPutUrl(anyString(), anyString(), anyLong());
		verify(storageDeletionService, never()).requestImageDeletion(any());
		verify(storageDeletionService, never()).processImageDeletion(any());
	}

}
