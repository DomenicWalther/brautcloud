package com.domenicwalther.brautcloud.dto;

import com.domenicwalther.brautcloud.model.Event;

import java.time.LocalDateTime;
import java.util.UUID;

public record PublicEventResponse(UUID id, String eventName, String location, LocalDateTime date,
		String firstNameCoupleOne, String firstNameCoupleTwo, boolean passwordProtected) {

	public static PublicEventResponse fromEvent(Event event) {
		return new PublicEventResponse(event.getId(), event.getEventName(), event.getLocation(), event.getDate(),
				event.getFirstNameCoupleOne(), event.getFirstNameCoupleTwo(),
				event.getPassword() != null && !event.getPassword().isBlank());
	}

}
