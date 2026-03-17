package com.domenicwalther.brautcloud.dto;

import com.domenicwalther.brautcloud.model.Event;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@AllArgsConstructor
@Data
public class EventResponse {

	private Long id;

	private String eventName;

	private String location;

	private LocalDateTime date;

	private Long userId;

	private String firstNameCoupleOne;

	private String firstNameCoupleTwo;

	public static EventResponse fromEvent(Event event) {
		return new EventResponse(event.getId(), event.getEventName(), event.getLocation(), event.getDate(),
				event.getUser().getId(), event.getFirstNameCoupleOne(), event.getFirstNameCoupleTwo());
	}

}