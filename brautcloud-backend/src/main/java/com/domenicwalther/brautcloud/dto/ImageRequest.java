package com.domenicwalther.brautcloud.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@AllArgsConstructor
@Data
public class ImageRequest {

	private UUID eventId;

	private MultipartFile file;

}
