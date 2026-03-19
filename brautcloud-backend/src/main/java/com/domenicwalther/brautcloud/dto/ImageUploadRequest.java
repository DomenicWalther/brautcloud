package com.domenicwalther.brautcloud.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ImageUploadRequest {

	private UUID eventId;

	private List<String> fileNames;

}
