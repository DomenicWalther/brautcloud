package com.domenicwalther.brautcloud.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Read model for the authenticated event list. Keeps guest counts in the same database
 * query as event data instead of loading one count per event.
 */
public record EventSummary(UUID id, String eventName, String location, LocalDateTime date, UUID userId,
		String firstNameCoupleOne, String firstNameCoupleTwo, long viewCount, long guestCount, String password) {

	public EventResponse toResponse() {
		return new EventResponse(id, eventName, location, date, userId, firstNameCoupleOne, firstNameCoupleTwo,
				viewCount, guestCount, password != null && !password.isBlank());
	}

}
