package com.domenicwalther.brautcloud.service;

import com.domenicwalther.brautcloud.exception.StorageLifecycleException;
import com.domenicwalther.brautcloud.model.Event;
import com.domenicwalther.brautcloud.model.EventLifecycleState;
import com.domenicwalther.brautcloud.model.Image;
import com.domenicwalther.brautcloud.model.ImageLifecycleState;
import com.domenicwalther.brautcloud.model.StorageDeletionJob;
import com.domenicwalther.brautcloud.model.StorageDeletionResourceType;
import com.domenicwalther.brautcloud.repository.EventRepository;
import com.domenicwalther.brautcloud.repository.ImageRepository;
import com.domenicwalther.brautcloud.repository.StorageDeletionJobRepository;
import com.domenicwalther.brautcloud.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorageDeletionServiceTest {

	@Mock
	private S3Service s3Service;

	@Mock
	private EventRepository eventRepository;

	@Mock
	private ImageRepository imageRepository;

	@Mock
	private StorageDeletionJobRepository jobRepository;

	private StorageDeletionService storageDeletionService;

	@BeforeEach
	void setUp() {
		storageDeletionService = new StorageDeletionService(s3Service, eventRepository, imageRepository, jobRepository);
	}

	@Test
	void storageFailureKeepsDatabaseReferenceAndRecordsRetry() {
		Image image = image("photo.jpg");
		image.setDeletionRequested(true);
		StorageDeletionJob job = imageJob(image);
		when(imageRepository.findById(image.getId())).thenReturn(Optional.of(image));
		when(jobRepository.findByResourceTypeAndResourceId(StorageDeletionResourceType.IMAGE, image.getId()))
			.thenReturn(Optional.of(job));
		doThrow(new IllegalStateException("object store unavailable")).when(s3Service).deleteFile("photo.jpg");

		assertThatThrownBy(() -> storageDeletionService.processImageDeletion(image.getId()))
			.isInstanceOf(StorageLifecycleException.class)
			.hasMessage("Image deletion is pending object storage");

		assertThat(image.isDeletionRequested()).isTrue();
		assertThat(job.getAttempts()).isEqualTo(1);
		assertThat(job.getLastError()).isEqualTo("object store unavailable");
		verify(imageRepository, never()).deleteById(image.getId());
		verify(jobRepository).save(job);
	}

	@Test
	void databaseFailureAfterObjectDeleteIsSafeToRetryIdempotently() {
		Image image = image("photo.jpg");
		image.setDeletionRequested(true);
		StorageDeletionJob job = imageJob(image);
		when(imageRepository.findById(image.getId())).thenReturn(Optional.of(image));
		when(jobRepository.findByResourceTypeAndResourceId(StorageDeletionResourceType.IMAGE, image.getId()))
			.thenReturn(Optional.of(job));
		doThrow(new IllegalStateException("database unavailable")).doNothing()
			.when(imageRepository)
			.deleteById(image.getId());

		assertThatThrownBy(() -> storageDeletionService.processImageDeletion(image.getId()))
			.isInstanceOf(StorageLifecycleException.class)
			.hasMessage("Image deletion is pending database cleanup");
		storageDeletionService.processImageDeletion(image.getId());

		verify(s3Service, org.mockito.Mockito.times(2)).deleteFile("photo.jpg");
		verify(imageRepository, org.mockito.Mockito.times(2)).deleteById(image.getId());
		verify(jobRepository).deleteById(job.getId());
	}

	@Test
	void requestEventDeletionQueuesEveryImageBeforeEventJob() {
		Event event = TestFixtures.event(TestFixtures.user("owner@example.com"), "Wedding");
		event.setId(UUID.randomUUID());
		Image first = TestFixtures.image(event, "first.jpg", true);
		first.setId(UUID.randomUUID());
		Image second = TestFixtures.image(event, "second.jpg", true);
		second.setId(UUID.randomUUID());
		when(imageRepository.findByEventId(event.getId())).thenReturn(List.of(first, second));
		when(jobRepository.findByResourceTypeAndResourceId(any(StorageDeletionResourceType.class), any(UUID.class)))
			.thenReturn(Optional.empty());

		storageDeletionService.requestEventDeletion(event);

		assertThat(event.isDeletionRequested()).isTrue();
		assertThat(event.getLifecycleState()).isEqualTo(EventLifecycleState.DELETE_REQUESTED);
		assertThat(first.isDeletionRequested()).isTrue();
		assertThat(first.getLifecycleState()).isEqualTo(ImageLifecycleState.DELETE_REQUESTED);
		assertThat(second.isDeletionRequested()).isTrue();
		verify(eventRepository).save(event);
		verify(imageRepository).save(first);
		verify(imageRepository).save(second);
		verify(jobRepository, org.mockito.Mockito.times(3)).save(any(StorageDeletionJob.class));
	}

	@Test
	void scheduledWorkerClaimsAndReleasesJobLease() {
		Image image = image("photo.jpg");
		image.setDeletionRequested(true);
		StorageDeletionJob job = imageJob(image);
		String leaseToken = "worker-token";
		when(jobRepository.claimDueJobs(any(), any(), any(), org.mockito.ArgumentMatchers.eq(100))).thenReturn(1);
		when(jobRepository.findByLeaseToken(anyString())).thenReturn(List.of(job));
		when(jobRepository.findByIdAndLeaseToken(eq(job.getId()), anyString())).thenReturn(Optional.of(job));
		when(imageRepository.findByDeletionRequestedTrue()).thenReturn(List.of());
		when(eventRepository.findByDeletionRequestedTrue()).thenReturn(List.of());

		storageDeletionService.retryPendingDeletions();

		verify(jobRepository).claimDueJobs(any(), any(), anyString(), org.mockito.ArgumentMatchers.eq(100));
		verify(s3Service).deleteFile("photo.jpg");
		verify(jobRepository).deleteByIdAndLeaseToken(eq(job.getId()), anyString());
	}

	@Test
	void missingImageRowCanStillBeRetriedAndJobIsRemoved() {
		UUID imageId = UUID.randomUUID();
		StorageDeletionJob job = StorageDeletionJob.builder()
			.id(UUID.randomUUID())
			.resourceType(StorageDeletionResourceType.IMAGE)
			.resourceId(imageId)
			.eventId(UUID.randomUUID())
			.objectKey("already-missing.jpg")
			.nextAttemptAt(LocalDateTime.now())
			.build();
		when(jobRepository.findByResourceTypeAndResourceId(StorageDeletionResourceType.IMAGE, imageId))
			.thenReturn(Optional.of(job));
		when(imageRepository.findById(imageId)).thenReturn(Optional.empty());

		storageDeletionService.processImageDeletion(imageId);

		verify(s3Service).deleteFile("already-missing.jpg");
		verify(jobRepository).deleteById(job.getId());
	}

	private Image image(String key) {
		Event event = TestFixtures.event(TestFixtures.user("owner@example.com"), "Wedding");
		event.setId(UUID.randomUUID());
		Image image = TestFixtures.image(event, key, true);
		image.setId(UUID.randomUUID());
		return image;
	}

	private StorageDeletionJob imageJob(Image image) {
		return StorageDeletionJob.builder()
			.id(UUID.randomUUID())
			.resourceType(StorageDeletionResourceType.IMAGE)
			.resourceId(image.getId())
			.eventId(image.getEvent().getId())
			.objectKey(image.getImageKey())
			.nextAttemptAt(LocalDateTime.now())
			.build();
	}

}
