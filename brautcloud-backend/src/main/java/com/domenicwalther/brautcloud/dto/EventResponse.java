package com.domenicwalther.brautcloud.dto;

import com.domenicwalther.brautcloud.model.Event;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@AllArgsConstructor
@Data
public class EventResponse {

	private UUID id;

	private String eventName;

	private String location;

	private LocalDateTime date;

	private UUID userId;

	private String firstNameCoupleOne;

	private String firstNameCoupleTwo;

	private long viewCount;

	private long guestCount;

	private boolean hasPassword;

	public static EventResponse fromEvent(Event event) {
		return fromEvent(event, 0L);
	}

	public static EventResponse fromEvent(Event event, long guestCount) {
		return new EventResponse(event.getId(), event.getEventName(), event.getLocation(), event.getDate(),
				event.getUser().getId(), event.getFirstNameCoupleOne(), event.getFirstNameCoupleTwo(),
				event.getViewCount(), guestCount, event.getPassword() != null && !event.getPassword().isBlank());
	}

}