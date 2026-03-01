package com.domenicwalther.brautcloud.service;

import com.domenicwalther.brautcloud.dto.ImageRequest;
import com.domenicwalther.brautcloud.dto.ImageResponse;
import com.domenicwalther.brautcloud.model.Event;
import com.domenicwalther.brautcloud.model.Image;
import com.domenicwalther.brautcloud.repository.EventRepository;
import com.domenicwalther.brautcloud.repository.ImageRepository;
import org.apache.coyote.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.List;

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

	private ResponseEntity<String> uploadFile(MultipartFile file) {
		try {
			File tempFile = File.createTempFile("upload-", file.getOriginalFilename());
			file.transferTo(tempFile);

			s3Service.uploadFile(file.getOriginalFilename(), tempFile);

			return ResponseEntity.ok("File uploaded successfully");
		}
		catch (Exception e) {
			return ResponseEntity.status(500).body("Uploaded failed: " + e.getMessage());
		}
	}

	public ResponseEntity<String> createNewImage(ImageRequest request) {
		Event event = eventRepository.findById(request.getEventId())
			.orElseThrow(() -> new RuntimeException("Event not found"));
		Image image = new Image();
		image.setVisible(true);
		image.setImageKey(request.getFile().getOriginalFilename());
		image.setEvent(event);
		imageRepository.save(image);
		return uploadFile(request.getFile());
	}

	public List<ImageResponse> getAllImagesByEventID(Long eventID) {
		return imageRepository.findByEventId(eventID).stream().map(ImageResponse::fromImage).toList();
	}

	public ResponseEntity<String> deleteImageByImageID(Long imageID) {
		Image image = imageRepository.findById(imageID).orElseThrow(() -> new RuntimeException("Event not found"));
		imageRepository.deleteById(imageID);
		String imageKey = image.getImageKey();
		s3Service.deleteFile(imageKey);
		return new ResponseEntity<>(HttpStatus.OK);
	}

}
