package com.domenicwalther.brautcloud.controller;

import com.domenicwalther.brautcloud.dto.ImageUploadRequest;
import com.domenicwalther.brautcloud.dto.ImageUploadResponse;
import com.domenicwalther.brautcloud.service.ImageService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
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
	public ResponseEntity<List<ImageUploadResponse>> getPresignedUrls(
			@AuthenticationPrincipal UserDetails authenticatedUser, @RequestBody ImageUploadRequest request) {
		return ResponseEntity.ok(imageService.generatePresignedUploadUrls(authenticatedUser.getUsername(), request));
	}

	@PostMapping("/uploaded")
	public ResponseEntity<Void> markAsUploaded(@AuthenticationPrincipal UserDetails authenticatedUser,
			@RequestBody List<UUID> imageIds) {
		imageService.markImagesAsUploaded(authenticatedUser.getUsername(), imageIds);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("{imageID}")
	public ResponseEntity<Void> deleteFile(@AuthenticationPrincipal UserDetails authenticatedUser,
			@PathVariable UUID imageID) {
		imageService.deleteImageByImageID(authenticatedUser.getUsername(), imageID);
		return ResponseEntity.noContent().build();
	}

}
