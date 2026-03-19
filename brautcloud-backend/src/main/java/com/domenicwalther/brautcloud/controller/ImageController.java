package com.domenicwalther.brautcloud.controller;

import com.domenicwalther.brautcloud.dto.ImageRequest;
import com.domenicwalther.brautcloud.dto.ImageResponse;
import com.domenicwalther.brautcloud.model.Image;
import com.domenicwalther.brautcloud.service.ImageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RequestMapping("/api/image")
@RestController
public class ImageController {

	ImageService imageService;

	public ImageController(ImageService imageService) {
		this.imageService = imageService;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<Void> uploadFile(@RequestPart("file") MultipartFile file,
			@RequestParam("eventId") UUID eventId) {
		ImageRequest request = new ImageRequest(eventId, file);
		imageService.createNewImage(request);
		return ResponseEntity.status(HttpStatus.CREATED).build();
	}

	@DeleteMapping("{imageID}")
	public ResponseEntity<Void> deleteFile(@PathVariable UUID imageID) {
		imageService.deleteImageByImageID(imageID);
		return ResponseEntity.noContent().build();
	}

}
