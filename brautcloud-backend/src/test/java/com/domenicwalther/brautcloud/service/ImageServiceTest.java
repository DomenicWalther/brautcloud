package com.domenicwalther.brautcloud.service;

import com.domenicwalther.brautcloud.dto.ImageUploadRequest;
import com.domenicwalther.brautcloud.dto.ImageUploadResponse;
import com.domenicwalther.brautcloud.exception.ResourceNotFoundException;
import com.domenicwalther.brautcloud.model.Event;
import com.domenicwalther.brautcloud.model.Image;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageServiceTest {

	@Mock
	private ImageRepository imageRepository;

	@Mock
	private EventRepository eventRepository;

	@Mock
	private S3Service s3Service;

	@Mock
	private EventService eventService;

	private ImageService imageService;

	@BeforeEach
	void setUp() {
		ResourceOwnershipService resourceOwnershipService = new ResourceOwnershipService(eventRepository,
				imageRepository);
		imageService = new ImageService(imageRepository, resourceOwnershipService, eventService);
		ReflectionTestUtils.setField(imageService, "s3Service", s3Service);
	}

	@Test
	void uploadUrlsCreatePendingImageRecordsForEachFile() {
		UUID eventId = UUID.randomUUID();
		User owner = TestFixtures.user("owner@example.com");
		Event event = TestFixtures.event(owner, "Wedding");
		event.setId(eventId);
		ImageUploadRequest request = new ImageUploadRequest(eventId, List.of("ceremony.jpg", "party.jpg"));
		AtomicInteger sequence = new AtomicInteger();
		when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
		when(imageRepository.save(any(Image.class))).thenAnswer(invocation -> {
			Image image = invocation.getArgument(0);
			image.setId(new UUID(0, sequence.incrementAndGet()));
			return image;
		});
		when(s3Service.getPresignedPutUrl(any(String.class)))
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
		verify(s3Service, never()).getPresignedPutUrl(any());
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
		when(s3Service.getPresignedPutUrl(any(String.class))).thenReturn("https://uploads.test/photo");

		List<ImageUploadResponse> responses = imageService.generatePublicPresignedUploadUrls(eventId, null,
				List.of("guest photo.jpg"));

		assertThat(responses).singleElement().satisfies(response -> {
			assertThat(response.getImageId()).isNotNull();
			assertThat(response.getUploadUrl()).isEqualTo("https://uploads.test/photo");
		});
		ArgumentCaptor<Image> image = ArgumentCaptor.forClass(Image.class);
		verify(imageRepository).save(image.capture());
		assertThat(image.getValue().getEvent()).isSameAs(event);
		assertThat(image.getValue().isUploaded()).isFalse();
		ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
		verify(s3Service).getPresignedPutUrl(key.capture());
		assertThat(key.getValue()).endsWith("-guest_photo.jpg");
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

		assertThatThrownBy(() -> imageService.markPublicImagesAsUploaded(eventId, "secret", List.of(imageId)))
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

		imageService.markPublicImagesAsUploaded(eventId, "secret", List.of(imageId));

		assertThat(image.isUploaded()).isTrue();
		verify(imageRepository).saveAll(List.of(image));
	}

	@Test
	void publicUploadRejectsInvalidFileNamesBeforeCreatingMetadata() {
		UUID eventId = UUID.randomUUID();
		when(eventService.requirePublicGalleryAccess(eventId, null)).thenReturn(new Event());

		assertThatThrownBy(() -> imageService.generatePublicPresignedUploadUrls(eventId, null, List.of()))
			.isInstanceOf(com.domenicwalther.brautcloud.exception.BadRequestException.class);
		verify(imageRepository, never()).save(any());
		verify(s3Service, never()).getPresignedPutUrl(any());
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
		when(imageRepository.findAllById(List.of(firstId, secondId))).thenReturn(List.of(first, second));

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

		verify(imageRepository).deleteById(imageId);
		verify(s3Service).deleteFile("event/photo.jpg");
	}

	@Test
	void deletingUnknownImageDoesNotCallS3() {
		UUID imageId = UUID.randomUUID();
		when(imageRepository.findById(imageId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> imageService.deleteImageByImageID("owner@example.com", imageId))
			.isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("Image not found");
		verify(imageRepository, never()).deleteById(any());
		verify(s3Service, never()).deleteFile(any());
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
		verify(s3Service, never()).getPresignedPutUrl(any());
		verify(s3Service, never()).deleteFile(any());
	}

}
