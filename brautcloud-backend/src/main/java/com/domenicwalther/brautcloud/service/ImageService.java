package com.domenicwalther.brautcloud.service;

import com.domenicwalther.brautcloud.dto.ImageUploadRequest;
import com.domenicwalther.brautcloud.dto.ImageUploadResponse;
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

	public ImageService(ImageRepository imageRepository, ResourceOwnershipService resourceOwnershipService) {
		this.imageRepository = imageRepository;
		this.resourceOwnershipService = resourceOwnershipService;
	}

	public List<ImageUploadResponse> generatePresignedUploadUrls(String email, ImageUploadRequest request) {
		Event event = resourceOwnershipService.requireOwnedEvent(email, request.getEventId());

		return request.getFileNames().stream().map(fileName -> {
			String key = UUID.randomUUID() + "-" + fileName;
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
		images.forEach(image -> image.setUploaded(true));
		imageRepository.saveAll(images);
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
