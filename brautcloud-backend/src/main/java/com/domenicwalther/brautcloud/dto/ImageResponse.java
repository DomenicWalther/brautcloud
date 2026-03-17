package com.domenicwalther.brautcloud.dto;

import com.domenicwalther.brautcloud.model.Event;
import com.domenicwalther.brautcloud.model.Image;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@AllArgsConstructor
@Data
public class ImageResponse {

	private UUID id;

	private String imageKey;

	private boolean isVisible;

	private UUID eventId;

	public static ImageResponse fromImage(Image image) {
		return new ImageResponse(image.getId(), image.getImageKey(), image.isVisible(), image.getEvent().getId());

	}

}
