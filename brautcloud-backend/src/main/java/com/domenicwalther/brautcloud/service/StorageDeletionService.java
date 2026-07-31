package com.domenicwalther.brautcloud.service;

import com.domenicwalther.brautcloud.exception.StorageLifecycleException;
import com.domenicwalther.brautcloud.model.Event;
import com.domenicwalther.brautcloud.model.Image;
import com.domenicwalther.brautcloud.model.StorageDeletionJob;
import com.domenicwalther.brautcloud.model.StorageDeletionResourceType;
import com.domenicwalther.brautcloud.repository.EventRepository;
import com.domenicwalther.brautcloud.repository.ImageRepository;
import com.domenicwalther.brautcloud.repository.StorageDeletionJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class StorageDeletionService {

	private static final Logger log = LoggerFactory.getLogger(StorageDeletionService.class);

	private static final int MAX_RETRY_DELAY_MINUTES = 60;

	private final S3Service s3Service;

	private final EventRepository eventRepository;

	private final ImageRepository imageRepository;

	private final StorageDeletionJobRepository jobRepository;

	public StorageDeletionService(S3Service s3Service, EventRepository eventRepository, ImageRepository imageRepository,
			StorageDeletionJobRepository jobRepository) {
		this.s3Service = s3Service;
		this.eventRepository = eventRepository;
		this.imageRepository = imageRepository;
		this.jobRepository = jobRepository;
	}

	/**
	 * Commits a deletion marker and durable outbox row before any object-storage call.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void requestImageDeletion(Image image) {
		if (!image.isDeletionRequested()) {
			image.setDeletionRequested(true);
			imageRepository.save(image);
		}
		ensureJob(StorageDeletionResourceType.IMAGE, image.getId(), image.getEvent().getId(), image.getImageKey());
	}

	/**
	 * Commits event and image deletion markers plus all durable outbox rows before any
	 * object-storage call.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void requestEventDeletion(Event event) {
		if (!event.isDeletionRequested()) {
			event.setDeletionRequested(true);
			eventRepository.save(event);
		}
		for (Image image : imageRepository.findByEventId(event.getId())) {
			requestImageDeletion(image);
		}
		ensureJob(StorageDeletionResourceType.EVENT, event.getId(), event.getId(), null);
	}

	public void processImageDeletion(UUID imageId) {
		StorageDeletionJob job = findOrRecoverImageJob(imageId).orElse(null);
		if (job == null) {
			return;
		}
		processImageJob(job);
	}

	public void processEventDeletion(UUID eventId) {
		StorageDeletionJob eventJob = jobRepository
			.findByResourceTypeAndResourceId(StorageDeletionResourceType.EVENT, eventId)
			.orElse(null);
		if (eventJob == null) {
			return;
		}
		Event event;
		try {
			event = eventRepository.findById(eventId).orElse(null);
		}
		catch (RuntimeException ex) {
			markFailure(eventJob, ex);
			throw new StorageLifecycleException("Event deletion is pending database access", ex);
		}
		if (event != null && !event.isDeletionRequested()) {
			// A stale job must never delete a live event or its image references.
			jobRepository.deleteById(eventJob.getId());
			return;
		}

		// Reconcile rows in case the request transaction was interrupted after the
		// event marker was persisted but before every image job was written.
		List<Image> eventImages;
		try {
			eventImages = imageRepository.findByEventId(eventId);
		}
		catch (RuntimeException ex) {
			markFailure(eventJob, ex);
			throw new StorageLifecycleException("Event deletion is pending database access", ex);
		}
		for (Image image : eventImages) {
			if (!image.isDeletionRequested()) {
				image.setDeletionRequested(true);
				imageRepository.save(image);
			}
			ensureJob(StorageDeletionResourceType.IMAGE, image.getId(), eventId, image.getImageKey());
		}

		RuntimeException firstFailure = null;
		List<StorageDeletionJob> imageJobs;
		try {
			imageJobs = jobRepository.findByEventIdAndResourceTypeOrderByCreatedAtAsc(eventId,
					StorageDeletionResourceType.IMAGE);
		}
		catch (RuntimeException ex) {
			markFailure(eventJob, ex);
			throw new StorageLifecycleException("Event deletion is pending database access", ex);
		}
		for (StorageDeletionJob imageJob : imageJobs) {
			try {
				processImageJob(imageJob);
			}
			catch (RuntimeException ex) {
				if (firstFailure == null) {
					firstFailure = ex;
				}
			}
		}

		if (firstFailure != null) {
			markFailure(eventJob, firstFailure);
			throw firstFailure;
		}
		try {
			if (!imageRepository.findByEventId(eventId).isEmpty()) {
				StorageLifecycleException pending = new StorageLifecycleException(
						"Event deletion is pending database cleanup", null);
				markFailure(eventJob, pending);
				throw pending;
			}
		}
		catch (StorageLifecycleException ex) {
			throw ex;
		}
		catch (RuntimeException ex) {
			markFailure(eventJob, ex);
			throw new StorageLifecycleException("Event deletion is pending database access", ex);
		}

		try {
			if (event != null) {
				eventRepository.deleteById(eventId);
			}
			jobRepository.deleteById(eventJob.getId());
		}
		catch (RuntimeException ex) {
			markFailure(eventJob, ex);
			throw new StorageLifecycleException("Event deletion is pending database cleanup", ex);
		}
	}

	/**
	 * Rebuilds missing jobs from deletion markers, then retries due jobs. This is also
	 * the reconciliation path for a database failure between marker and job writes.
	 */
	@Scheduled(cron = "0 * * * * *")
	public void retryPendingDeletions() {
		try {
			reconcileRequestedImages();
			reconcileRequestedEvents();
			for (StorageDeletionJob job : jobRepository
				.findByNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(LocalDateTime.now())) {
				try {
					if (job.getResourceType() == StorageDeletionResourceType.IMAGE) {
						processImageJob(job);
					}
					else {
						processEventDeletion(job.getResourceId());
					}
				}
				catch (RuntimeException ex) {
					log.warn("Storage deletion job {} remains queued: {}", job.getId(), ex.getMessage());
				}
			}
		}
		catch (RuntimeException ex) {
			log.warn("Storage deletion reconciliation failed: {}", ex.getMessage());
		}
	}

	private void reconcileRequestedImages() {
		for (Image image : imageRepository.findByDeletionRequestedTrue()) {
			ensureJob(StorageDeletionResourceType.IMAGE, image.getId(), image.getEvent().getId(), image.getImageKey());
		}
	}

	private void reconcileRequestedEvents() {
		for (Event event : eventRepository.findByDeletionRequestedTrue()) {
			requestEventDeletion(event);
		}
	}

	private Optional<StorageDeletionJob> findOrRecoverImageJob(UUID imageId) {
		Optional<StorageDeletionJob> existing = jobRepository
			.findByResourceTypeAndResourceId(StorageDeletionResourceType.IMAGE, imageId);
		if (existing.isPresent()) {
			return existing;
		}
		Image image = imageRepository.findById(imageId).orElse(null);
		if (image == null || !image.isDeletionRequested()) {
			return Optional.empty();
		}
		ensureJob(StorageDeletionResourceType.IMAGE, image.getId(), image.getEvent().getId(), image.getImageKey());
		return jobRepository.findByResourceTypeAndResourceId(StorageDeletionResourceType.IMAGE, imageId);
	}

	private void processImageJob(StorageDeletionJob job) {
		Image image;
		try {
			image = imageRepository.findById(job.getResourceId()).orElse(null);
		}
		catch (RuntimeException ex) {
			markFailure(job, ex);
			throw new StorageLifecycleException("Image deletion is pending database access", ex);
		}
		if (image != null && !image.isDeletionRequested()) {
			// A stale job must never delete an object for a live database reference.
			try {
				jobRepository.deleteById(job.getId());
			}
			catch (RuntimeException ex) {
				markFailure(job, ex);
				throw new StorageLifecycleException("Stale image deletion job cleanup is pending", ex);
			}
			return;
		}

		try {
			s3Service.deleteFile(job.getObjectKey());
		}
		catch (RuntimeException ex) {
			markFailure(job, ex);
			throw new StorageLifecycleException("Image deletion is pending object storage", ex);
		}

		try {
			if (image != null) {
				imageRepository.deleteById(image.getId());
			}
			jobRepository.deleteById(job.getId());
		}
		catch (RuntimeException ex) {
			// Object deletion already succeeded. Keeping this job makes the database
			// cleanup retryable, and DeleteObject is idempotent on S3-compatible APIs.
			markFailure(job, ex);
			throw new StorageLifecycleException("Image deletion is pending database cleanup", ex);
		}
	}

	private void ensureJob(StorageDeletionResourceType resourceType, UUID resourceId, UUID eventId, String objectKey) {
		if (jobRepository.findByResourceTypeAndResourceId(resourceType, resourceId).isPresent()) {
			return;
		}
		StorageDeletionJob job = StorageDeletionJob.builder()
			.resourceType(resourceType)
			.resourceId(resourceId)
			.eventId(eventId)
			.objectKey(objectKey)
			.nextAttemptAt(LocalDateTime.now())
			.build();
		jobRepository.save(job);
	}

	private void markFailure(StorageDeletionJob job, RuntimeException failure) {
		try {
			int attempts = job.getAttempts() + 1;
			job.setAttempts(attempts);
			job.setLastError(errorMessage(failure));
			long delay = Math.min(MAX_RETRY_DELAY_MINUTES, 1L << Math.min(attempts, 6));
			job.setNextAttemptAt(LocalDateTime.now().plusMinutes(delay));
			jobRepository.save(job);
		}
		catch (RuntimeException recordFailure) {
			log.error("Could not record storage deletion failure for job {}", job.getId(), recordFailure);
		}
	}

	private String errorMessage(RuntimeException failure) {
		String message = failure.getMessage();
		return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
	}

}
