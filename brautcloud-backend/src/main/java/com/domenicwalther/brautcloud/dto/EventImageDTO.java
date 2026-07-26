package com.domenicwalther.brautcloud.dto;

import java.util.UUID;

public record EventImageDTO(UUID id, String url, boolean canDelete) {

	public EventImageDTO(UUID id, String url) {
		this(id, url, false);
	}

}
