package com.domenicwalther.brautcloud.service;

import com.domenicwalther.brautcloud.dto.ImageUploadRequest;
import com.domenicwalther.brautcloud.dto.ImageUploadResponse;
import com.domenicwalther.brautcloud.exception.BadRequestException;
import com.domenicwalther.brautcloud.exception.ResourceNotFoundException;
import com.domenicwalther.brautcloud.model.Event;
import com.domenicwalther.brautcloud.model.Image;
import com.domenicwalther.brautcloud.repository.ImageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ImageService {

	@Autowired
	private S3Service s3Service;

	private final ImageRepository imageRepository;

	private final ResourceOwnershipService resourceOwnershipService;

	private final EventService eventService;

	public ImageService(ImageRepository imageRepository, ResourceOwnershipService resourceOwnershipService,
			EventService eventService) {
		this.imageRepository = imageRepository;
		this.resourceOwnershipService = resourceOwnershipService;
		this.eventService = eventService;
	}

	public List<ImageUploadResponse> generatePresignedUploadUrls(String email, ImageUploadRequest request) {
		Event event = resourceOwnershipService.requireOwnedEvent(email, request.getEventId());
		return generatePresignedUploadUrls(event, request.getFileNames());
	}

	public List<ImageUploadResponse> generatePublicPresignedUploadUrls(UUID eventID, String galleryPassword,
			List<String> fileNames) {
		Event event = eventService.requirePublicGalleryAccess(eventID, galleryPassword);
		return generatePresignedUploadUrls(event, fileNames);
	}

	private List<ImageUploadResponse> generatePresignedUploadUrls(Event event, List<String> fileNames) {
		validateFileNames(fileNames);
		return fileNames.stream().map(fileName -> {
			String key = UUID.randomUUID() + "-" + sanitizeFileName(fileName);
			Image image = new Image();
			image.setEvent(event);
			image.setImageKey(key);
			image.setVisible(true);
			image.setUploaded(false);
			imageRepository.save(image);

			String uploadUrl = s3Service.getPresignedPutUrl(key);
			return new ImageUploadResponse(image.getId(), uploadUrl);
		}).collect(Collectors.toList());
	}

	public void markImagesAsUploaded(String email, List<UUID> imageIds) {
		List<Image> images = resourceOwnershipService.requireOwnedImages(email, imageIds);
		markImagesAsUploaded(images);
	}

	public void markPublicImagesAsUploaded(UUID eventID, String galleryPassword, List<UUID> imageIds) {
		Event event = eventService.requirePublicGalleryAccess(eventID, galleryPassword);
		validateImageIds(imageIds);
		List<Image> images = imageRepository.findAllById(imageIds);
		if (images.size() != imageIds.size()
				|| images.stream().anyMatch(image -> !event.getId().equals(image.getEvent().getId()))) {
			throw new ResourceNotFoundException("Image not found");
		}
		markImagesAsUploaded(images);
	}

	private void markImagesAsUploaded(List<Image> images) {
		images.forEach(image -> image.setUploaded(true));
		imageRepository.saveAll(images);
	}

	private void validateFileNames(List<String> fileNames) {
		if (fileNames == null || fileNames.isEmpty() || fileNames.size() > 100
				|| fileNames.stream().anyMatch(fileName -> fileName == null || fileName.isBlank())) {
			throw new BadRequestException("At least one valid file name is required");
		}
	}

	private String sanitizeFileName(String fileName) {
		return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
	}

	private void validateImageIds(List<UUID> imageIds) {
		if (imageIds == null || imageIds.isEmpty() || imageIds.stream().distinct().count() != imageIds.size()) {
			throw new ResourceNotFoundException("Image not found");
		}
	}

	public void deleteImageByImageID(String email, UUID imageID) {
		Image image = resourceOwnershipService.requireOwnedImage(email, imageID);
		imageRepository.deleteById(imageID);
		String imageKey = image.getImageKey();
		s3Service.deleteFile(imageKey);
	}

	@Scheduled(cron = "0 0 * * * *") // Every hour
	public void cleanupUnuploadedImages() {
		LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
		imageRepository.findByIsUploadedFalseAndCreatedAtBefore(oneHourAgo).forEach(imageRepository::delete);
	}

}
