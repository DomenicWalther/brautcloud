package com.domenicwalther.brautcloud.controller;

import com.domenicwalther.brautcloud.dto.ImageUploadRequest;
import com.domenicwalther.brautcloud.dto.ImageUploadResponse;
import com.domenicwalther.brautcloud.service.ImageService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
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
		String email = SecurityContextHolder.getContext().getAuthentication().getName();
		return ResponseEntity.ok(imageService.generatePresignedUploadUrls(email, request));
	}

	@PostMapping("/uploaded")
	public ResponseEntity<Void> markAsUploaded(@RequestBody List<UUID> imageIds) {
		String email = SecurityContextHolder.getContext().getAuthentication().getName();
		imageService.markImagesAsUploaded(email, imageIds);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("{imageID}")
	public ResponseEntity<Void> deleteFile(@PathVariable UUID imageID) {
		String email = SecurityContextHolder.getContext().getAuthentication().getName();
		imageService.deleteImageByImageID(email, imageID);
		return ResponseEntity.noContent().build();
	}

}
