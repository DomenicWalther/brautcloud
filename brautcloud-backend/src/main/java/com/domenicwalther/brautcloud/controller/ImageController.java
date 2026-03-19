package com.domenicwalther.brautcloud.controller;

import com.domenicwalther.brautcloud.dto.ImageUploadRequest;
import com.domenicwalther.brautcloud.dto.ImageUploadResponse;
import com.domenicwalther.brautcloud.service.ImageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RequestMapping("/api/image")
@RestController
public class ImageController {

	ImageService imageService;

	public ImageController(ImageService imageService) {
		this.imageService = imageService;
	}

	@PostMapping("/presigned-url")
	public ResponseEntity<List<ImageUploadResponse>> getPresignedUrls(@RequestBody ImageUploadRequest request) {
		return ResponseEntity.ok(imageService.generatePresignedUploadUrls(request));
	}

	@PostMapping("/uploaded")
	public ResponseEntity<Void> markAsUploaded(@RequestBody List<UUID> imageIds) {
		imageService.markImagesAsUploaded(imageIds);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("{imageID}")
	public ResponseEntity<Void> deleteFile(@PathVariable UUID imageID) {
		imageService.deleteImageByImageID(imageID);
		return ResponseEntity.noContent().build();
	}

}
