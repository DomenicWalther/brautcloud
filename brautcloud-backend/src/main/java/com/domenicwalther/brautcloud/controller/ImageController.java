package com.domenicwalther.brautcloud.controller;

import com.domenicwalther.brautcloud.dto.ImageUploadRequest;
import com.domenicwalther.brautcloud.dto.ImageUploadResponse;
import com.domenicwalther.brautcloud.service.ImageService;
import com.domenicwalther.brautcloud.service.ImageUploadPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RequestMapping("/api/image")
@RestController
@Validated
public class ImageController {

	ImageService imageService;

	public ImageController(ImageService imageService) {
		this.imageService = imageService;
	}

	@PostMapping("/presigned-url")
	public ResponseEntity<List<ImageUploadResponse>> getPresignedUrls(
			@AuthenticationPrincipal UserDetails authenticatedUser, @Valid @RequestBody ImageUploadRequest request) {
		return ResponseEntity.ok(imageService.generatePresignedUploadUrls(authenticatedUser.getUsername(), request));
	}

	@PostMapping("/uploaded")
	public ResponseEntity<Void> markAsUploaded(@AuthenticationPrincipal UserDetails authenticatedUser,
			@Valid @RequestBody @Size(max = ImageUploadPolicy.MAX_FILES_PER_REQUEST,
					message = "At most 100 image IDs may be confirmed at once") List<UUID> imageIds) {
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
