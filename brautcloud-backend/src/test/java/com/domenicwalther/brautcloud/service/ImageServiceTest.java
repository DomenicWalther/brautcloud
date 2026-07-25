package com.domenicwalther.brautcloud.service;

import com.domenicwalther.brautcloud.dto.ImageUploadRequest;
import com.domenicwalther.brautcloud.dto.ImageUploadResponse;
import com.domenicwalther.brautcloud.exception.ResourceNotFoundException;
import com.domenicwalther.brautcloud.model.Event;
import com.domenicwalther.brautcloud.model.Image;
import com.domenicwalther.brautcloud.repository.EventRepository;
import com.domenicwalther.brautcloud.repository.ImageRepository;
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

	private ImageService imageService;

	@BeforeEach
	void setUp() {
		imageService = new ImageService(imageRepository, eventRepository);
		ReflectionTestUtils.setField(imageService, "s3Service", s3Service);
	}

	@Test
	void uploadUrlsCreatePendingImageRecordsForEachFile() {
		UUID eventId = UUID.randomUUID();
		Event event = new Event();
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

		List<ImageUploadResponse> responses = imageService.generatePresignedUploadUrls(request);

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

		assertThatThrownBy(() -> imageService.generatePresignedUploadUrls(request))
			.isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("Event not found");
		verify(imageRepository, never()).save(any());
		verify(s3Service, never()).getPresignedPutUrl(any());
	}

	@Test
	void markingUploadedOnlyUpdatesImagesFoundByRepository() {
		UUID firstId = UUID.randomUUID();
		UUID secondId = UUID.randomUUID();
		Image first = new Image();
		first.setId(firstId);
		Image second = new Image();
		second.setId(secondId);
		when(imageRepository.findAllById(List.of(firstId, secondId))).thenReturn(List.of(first, second));

		imageService.markImagesAsUploaded(List.of(firstId, secondId));

		assertThat(first.isUploaded()).isTrue();
		assertThat(second.isUploaded()).isTrue();
		verify(imageRepository).saveAll(List.of(first, second));
	}

	@Test
	void deletingImageRemovesMetadataAndObject() {
		UUID imageId = UUID.randomUUID();
		Image image = new Image();
		image.setId(imageId);
		image.setImageKey("event/photo.jpg");
		when(imageRepository.findById(imageId)).thenReturn(Optional.of(image));

		imageService.deleteImageByImageID(imageId);

		verify(imageRepository).deleteById(imageId);
		verify(s3Service).deleteFile("event/photo.jpg");
	}

	@Test
	void deletingUnknownImageDoesNotCallS3() {
		UUID imageId = UUID.randomUUID();
		when(imageRepository.findById(imageId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> imageService.deleteImageByImageID(imageId))
			.isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("Image not found");
		verify(imageRepository, never()).deleteById(any());
		verify(s3Service, never()).deleteFile(any());
	}

}
