package com.domenicwalther.brautcloud.dto;

import com.domenicwalther.brautcloud.model.Event;
import com.domenicwalther.brautcloud.model.Image;
import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class ImageResponse {

	private Long id;

	private String imageKey;

	private boolean isVisible;

	private Long eventId;

	public static ImageResponse fromImage(Image image) {
		return new ImageResponse(image.getId(), image.getImageKey(), image.isVisible(), image.getEvent().getId());

	}

}
