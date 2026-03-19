package com.domenicwalther.brautcloud.service;

import com.domenicwalther.brautcloud.dto.ImageRequest;
import com.domenicwalther.brautcloud.dto.ImageResponse;
import com.domenicwalther.brautcloud.exception.ResourceNotFoundException;
import com.domenicwalther.brautcloud.model.Event;
import com.domenicwalther.brautcloud.model.Image;
import com.domenicwalther.brautcloud.repository.EventRepository;
import com.domenicwalther.brautcloud.repository.ImageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

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

	private void uploadFile(MultipartFile file) {
		try {
			File tempFile = File.createTempFile("upload-", file.getOriginalFilename());
			file.transferTo(tempFile);

			s3Service.uploadFile(file.getOriginalFilename(), tempFile);
		}
		catch (IOException e) {
			throw new RuntimeException("Upload failed: " + e.getMessage());
		}
	}

	public void createNewImage(ImageRequest request) {
		Event event = eventRepository.findById(request.getEventId())
			.orElseThrow(() -> new ResourceNotFoundException("Event not found"));
		Image image = new Image();
		image.setVisible(true);
		image.setImageKey(request.getFile().getOriginalFilename());
		image.setEvent(event);
		imageRepository.save(image);
		uploadFile(request.getFile());
	}

	public void deleteImageByImageID(UUID imageID) {
		Image image = imageRepository.findById(imageID)
			.orElseThrow(() -> new ResourceNotFoundException("Image not found"));
		imageRepository.deleteById(imageID);
		String imageKey = image.getImageKey();
		s3Service.deleteFile(imageKey);
	}

}
