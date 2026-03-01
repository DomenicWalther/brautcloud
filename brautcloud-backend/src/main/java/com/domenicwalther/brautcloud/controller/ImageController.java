package com.domenicwalther.brautcloud.controller;

import com.domenicwalther.brautcloud.dto.ImageRequest;
import com.domenicwalther.brautcloud.dto.ImageResponse;
import com.domenicwalther.brautcloud.model.Image;
import com.domenicwalther.brautcloud.service.ImageService;
import com.domenicwalther.brautcloud.service.S3Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.List;

@RequestMapping("/api/image")
@RestController
public class ImageController {

	ImageService imageService;

	public ImageController(ImageService imageService) {
		this.imageService = imageService;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<String> uploadFile(@RequestPart("file") MultipartFile file,
			@RequestPart("eventId") String eventId) {
		ImageRequest request = new ImageRequest(Long.parseLong(eventId), file);
		return imageService.createNewImage(request);

	}

	@GetMapping("{eventID}")
	public List<ImageResponse> getAllImagesByEventID(@PathVariable Long eventID) {
		return imageService.getAllImagesByEventID(eventID);
	}

	@DeleteMapping("{imageID}")
	public ResponseEntity<String> deleteFile(@PathVariable Long imageID) {
		return imageService.deleteImageByImageID(imageID);
	}

}
