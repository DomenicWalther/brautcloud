package com.domenicwalther.brautcloud;

import com.domenicwalther.brautcloud.dto.ImageUploadRequest;
import com.domenicwalther.brautcloud.model.Event;
import com.domenicwalther.brautcloud.model.StorageDeletionJob;
import com.domenicwalther.brautcloud.model.StorageDeletionResourceType;
import com.domenicwalther.brautcloud.model.User;
import com.domenicwalther.brautcloud.repository.EventGuestVisitRepository;
import com.domenicwalther.brautcloud.repository.EventRepository;
import com.domenicwalther.brautcloud.repository.ImageRepository;
import com.domenicwalther.brautcloud.repository.RefreshTokenRepository;
import com.domenicwalther.brautcloud.repository.StorageDeletionJobRepository;
import com.domenicwalther.brautcloud.repository.UserRepository;
import com.domenicwalther.brautcloud.service.ImageService;
import com.domenicwalther.brautcloud.service.StorageDeletionService;
import com.domenicwalther.brautcloud.support.FullStackIntegrationTest;
import com.domenicwalther.brautcloud.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

class StorageDeletionConcurrencyIntegrationTest extends FullStackIntegrationTest {

	@Autowired
	private StorageDeletionJobRepository jobRepository;

	@Autowired
	private StorageDeletionService storageDeletionService;

	@Autowired
	private ImageService imageService;

	@Autowired
	private EventRepository eventRepository;

	@Autowired
	private ImageRepository imageRepository;

	@Autowired
	private EventGuestVisitRepository eventGuestVisitRepository;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Autowired
	private UserRepository userRepository;

	@BeforeEach
	void cleanDatabase() {
		jobRepository.deleteAll();
		refreshTokenRepository.deleteAll();
		eventGuestVisitRepository.deleteAll();
		imageRepository.deleteAll();
		eventRepository.deleteAll();
		userRepository.deleteAll();
		reset(s3Service);
		when(s3Service.getPresignedPutUrl(anyString(), anyString(), anyLong()))
			.thenReturn("https://uploads.test/photo");
	}

	@Test
	void twoWorkersCanClaimDueJobOnlyOnce() throws Exception {
		StorageDeletionJob job = jobRepository.saveAndFlush(StorageDeletionJob.builder()
			.resourceType(StorageDeletionResourceType.IMAGE)
			.resourceId(UUID.randomUUID())
			.eventId(UUID.randomUUID())
			.objectKey("photo.jpg")
			.nextAttemptAt(LocalDateTime.now().minusMinutes(1))
			.build());
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Callable<Integer> first = () -> jobRepository.claimDueJobs(LocalDateTime.now(),
					LocalDateTime.now().plusMinutes(5), "worker-one", 1);
			Callable<Integer> second = () -> jobRepository.claimDueJobs(LocalDateTime.now(),
					LocalDateTime.now().plusMinutes(5), "worker-two", 1);
			List<Future<Integer>> results = executor.invokeAll(List.of(first, second));

			assertThat(results.stream().map(this::get).mapToInt(Integer::intValue).sum()).isOne();
			assertThat(jobRepository.findById(job.getId()).orElseThrow().getLeaseToken()).isIn("worker-one",
					"worker-two");
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	void deletionAndPresignRaceNeverLeavesNewUploadAfterDeletion() throws Exception {
		User owner = userRepository.save(TestFixtures.user("race-owner@example.com"));
		Event event = eventRepository.saveAndFlush(TestFixtures.event(owner, "Race wedding"));
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<?> deletion = executor.submit(() -> {
				storageDeletionService.requestEventDeletion(event);
				storageDeletionService.processEventDeletion(event.getId());
			});
			Future<Boolean> presign = executor.submit(() -> {
				try {
					imageService.generatePresignedUploadUrls(owner.getEmail(),
							new ImageUploadRequest(event.getId(), List.of("race.jpg")));
					return true;
				}
				catch (RuntimeException expected) {
					return false;
				}
			});
			deletion.get();
			boolean presignSucceeded = presign.get();

			assertThat(imageRepository.findByEventId(event.getId())).isEmpty();
			assertThat(eventRepository.findById(event.getId())).isEmpty();
			assertThat(jobRepository.findByEventIdAndResourceTypeOrderByCreatedAtAsc(event.getId(),
					StorageDeletionResourceType.IMAGE))
				.isEmpty();
			if (!presignSucceeded) {
				assertThat(imageRepository.findAll()).isEmpty();
			}
		}
		finally {
			executor.shutdownNow();
		}
	}

	private Integer get(Future<Integer> future) {
		try {
			return future.get();
		}
		catch (Exception exception) {
			throw new AssertionError(exception);
		}
	}

}
