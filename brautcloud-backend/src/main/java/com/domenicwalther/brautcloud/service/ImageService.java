package com.domenicwalther.brautcloud.service;

import com.domenicwalther.brautcloud.dto.ImageUploadRequest;
import com.domenicwalther.brautcloud.dto.ImageUploadResponse;
import com.domenicwalther.brautcloud.exception.ResourceNotFoundException;
import com.domenicwalther.brautcloud.model.Event;
import com.domenicwalther.brautcloud.model.Image;
import com.domenicwalther.brautcloud.repository.EventRepository;
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

	private final EventRepository eventRepository;

	public ImageService(ImageRepository imageRepository, EventRepository eventRepository) {
		this.imageRepository = imageRepository;
		this.eventRepository = eventRepository;
	}

	public List<ImageUploadResponse> generatePresignedUploadUrls(ImageUploadRequest request) {
		Event event = eventRepository.findById(request.getEventId())
			.orElseThrow(() -> new ResourceNotFoundException("Event not found"));

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

	public void markImagesAsUploaded(List<UUID> imageIds) {
		List<Image> images = imageRepository.findAllById(imageIds);
		images.forEach(image -> image.setUploaded(true));
		imageRepository.saveAll(images);
	}

	public void deleteImageByImageID(UUID imageID) {
		Image image = imageRepository.findById(imageID)
			.orElseThrow(() -> new ResourceNotFoundException("Image not found"));
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
