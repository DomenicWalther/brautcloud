package com.domenicwalther.brautcloud.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class ImageUploadResponse {

	private UUID imageId;

	private String uploadUrl;

	private String contentType;

	public ImageUploadResponse(UUID imageId, String uploadUrl) {
		this(imageId, uploadUrl, null);
	}

}
