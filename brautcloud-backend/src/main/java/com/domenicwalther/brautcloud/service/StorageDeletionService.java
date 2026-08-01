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
import org.springframework.dao.DataIntegrityViolationException;
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

	private static final int CLAIM_BATCH_SIZE = 100;

	private static final int LEASE_MINUTES = 5;

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
	 * Commits an image lifecycle transition and durable outbox row before any
	 * object-storage call.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void requestImageDeletion(Image image) {
		if (!image.isDeletionStarted()) {
			image.requestDeletion();
			imageRepository.save(image);
		}
		ensureJob(StorageDeletionResourceType.IMAGE, image.getId(), image.getEvent().getId(), image.getImageKey());
	}

	/**
	 * Locks event before transitioning it. Upload presigning takes same lock, so one of
	 * deletion or presigning wins and later presigns see deletion state.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void requestEventDeletion(Event requestedEvent) {
		Event event = eventRepository.findByIdForUpdate(requestedEvent.getId()).orElse(requestedEvent);
		if (!event.isDeletionStarted()) {
			event.requestDeletion();
			eventRepository.save(event);
		}
		for (Image image : imageRepository.findByEventId(event.getId())) {
			if (!image.isDeletionStarted()) {
				image.requestDeletion();
				imageRepository.save(image);
			}
			ensureJob(StorageDeletionResourceType.IMAGE, image.getId(), event.getId(), image.getImageKey());
		}
		ensureJob(StorageDeletionResourceType.EVENT, event.getId(), event.getId(), null);
	}

	public void processImageDeletion(UUID imageId) {
		StorageDeletionJob job = findOrRecoverImageJob(imageId).orElse(null);
		if (job != null) {
			processImageJob(job, null);
		}
	}

	public void processEventDeletion(UUID eventId) {
		StorageDeletionJob eventJob = jobRepository
			.findByResourceTypeAndResourceId(StorageDeletionResourceType.EVENT, eventId)
			.orElse(null);
		if (eventJob != null) {
			processEventDeletion(eventId, eventJob, null);
		}
	}

	/**
	 * Claims due work with PostgreSQL row locks. A lease allows another instance to
	 * recover work after a crashed worker while preventing normal double processing.
	 */
	@Scheduled(cron = "0 * * * * *")
	public void retryPendingDeletions() {
		try {
			reconcileRequestedImages();
			reconcileRequestedEvents();
			LocalDateTime now = LocalDateTime.now();
			LocalDateTime leaseUntil = now.plusMinutes(LEASE_MINUTES);
			String leaseToken = UUID.randomUUID().toString();
			jobRepository.claimDueJobs(now, leaseUntil, leaseToken, CLAIM_BATCH_SIZE);
			for (StorageDeletionJob job : jobRepository.findByLeaseToken(leaseToken)) {
				try {
					if (job.getResourceType() == StorageDeletionResourceType.IMAGE) {
						processImageJob(job, leaseToken);
					}
					else {
						processEventDeletion(job.getResourceId(), job, leaseToken);
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

	private void processEventDeletion(UUID eventId, StorageDeletionJob eventJob, String leaseToken) {
		if (!ownsLease(eventJob, leaseToken)) {
			return;
		}
		Event event;
		try {
			event = eventRepository.findById(eventId).orElse(null);
		}
		catch (RuntimeException ex) {
			markFailure(eventJob, ex, leaseToken);
			throw new StorageLifecycleException("Event deletion is pending database access", ex);
		}
		if (event != null && !event.isDeletionStarted()) {
			deleteJob(eventJob, leaseToken);
			return;
		}
		if (event != null) {
			event.markDeleting();
			eventRepository.save(event);
		}

		try {
			for (Image image : imageRepository.findByEventId(eventId)) {
				if (!image.isDeletionStarted()) {
					image.requestDeletion();
					imageRepository.save(image);
				}
				ensureJob(StorageDeletionResourceType.IMAGE, image.getId(), eventId, image.getImageKey());
			}
		}
		catch (RuntimeException ex) {
			markFailure(eventJob, ex, leaseToken);
			throw new StorageLifecycleException("Event deletion is pending database access", ex);
		}

		RuntimeException firstFailure = null;
		List<StorageDeletionJob> imageJobs = jobRepository.findByEventIdAndResourceTypeOrderByCreatedAtAsc(eventId,
				StorageDeletionResourceType.IMAGE);
		for (StorageDeletionJob imageJob : imageJobs) {
			StorageDeletionJob claimedImageJob = imageJob;
			if (leaseToken != null) {
				LocalDateTime now = LocalDateTime.now();
				if (jobRepository.claimJob(imageJob.getId(), now, now.plusMinutes(LEASE_MINUTES), leaseToken) == 0) {
					continue;
				}
				claimedImageJob = jobRepository.findByIdAndLeaseToken(imageJob.getId(), leaseToken).orElse(null);
				if (claimedImageJob == null) {
					continue;
				}
			}
			try {
				processImageJob(claimedImageJob, leaseToken);
			}
			catch (RuntimeException ex) {
				if (firstFailure == null) {
					firstFailure = ex;
				}
			}
		}
		if (firstFailure != null) {
			markFailure(eventJob, firstFailure, leaseToken);
			throw firstFailure;
		}

		try {
			if (!imageRepository.findByEventId(eventId).isEmpty()) {
				StorageLifecycleException pending = new StorageLifecycleException(
						"Event deletion is pending database cleanup", null);
				markFailure(eventJob, pending, leaseToken);
				throw pending;
			}
			if (event != null) {
				eventRepository.deleteById(eventId);
			}
			deleteJob(eventJob, leaseToken);
		}
		catch (StorageLifecycleException ex) {
			throw ex;
		}
		catch (RuntimeException ex) {
			markFailure(eventJob, ex, leaseToken);
			throw new StorageLifecycleException("Event deletion is pending database cleanup", ex);
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
		if (image == null || !image.isDeletionStarted()) {
			return Optional.empty();
		}
		ensureJob(StorageDeletionResourceType.IMAGE, image.getId(), image.getEvent().getId(), image.getImageKey());
		return jobRepository.findByResourceTypeAndResourceId(StorageDeletionResourceType.IMAGE, imageId);
	}

	private void processImageJob(StorageDeletionJob job, String leaseToken) {
		if (!ownsLease(job, leaseToken)) {
			return;
		}
		Image image;
		try {
			image = imageRepository.findById(job.getResourceId()).orElse(null);
		}
		catch (RuntimeException ex) {
			markFailure(job, ex, leaseToken);
			throw new StorageLifecycleException("Image deletion is pending database access", ex);
		}
		if (image != null && !image.isDeletionStarted()) {
			deleteJob(job, leaseToken);
			return;
		}
		if (image != null) {
			image.markDeletionRetrying();
			imageRepository.save(image);
		}
		try {
			s3Service.deleteFile(job.getObjectKey());
		}
		catch (RuntimeException ex) {
			markFailure(job, ex, leaseToken);
			throw new StorageLifecycleException("Image deletion is pending object storage", ex);
		}
		try {
			if (image != null) {
				imageRepository.deleteById(image.getId());
			}
			deleteJob(job, leaseToken);
		}
		catch (RuntimeException ex) {
			// DeleteObject is idempotent. Retain job for database cleanup retry.
			markFailure(job, ex, leaseToken);
			throw new StorageLifecycleException("Image deletion is pending database cleanup", ex);
		}
	}

	private boolean ownsLease(StorageDeletionJob job, String leaseToken) {
		if (leaseToken == null) {
			return true;
		}
		return jobRepository.findByIdAndLeaseToken(job.getId(), leaseToken).isPresent();
	}

	private void deleteJob(StorageDeletionJob job, String leaseToken) {
		if (leaseToken == null) {
			jobRepository.deleteById(job.getId());
		}
		else {
			jobRepository.deleteByIdAndLeaseToken(job.getId(), leaseToken);
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
		try {
			jobRepository.save(job);
		}
		catch (DataIntegrityViolationException duplicate) {
			// Another instance won the unique resource key race.
		}
	}

	private void markFailure(StorageDeletionJob job, RuntimeException failure, String leaseToken) {
		int attempts = job.getAttempts() + 1;
		String error = errorMessage(failure);
		long delay = Math.min(MAX_RETRY_DELAY_MINUTES, 1L << Math.min(attempts, 6));
		LocalDateTime nextAttemptAt = LocalDateTime.now().plusMinutes(delay);
		try {
			if (leaseToken == null) {
				job.setAttempts(attempts);
				job.setLastError(error);
				job.setNextAttemptAt(nextAttemptAt);
				jobRepository.save(job);
			}
			else {
				jobRepository.recordFailure(job.getId(), leaseToken, error, nextAttemptAt);
			}
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
