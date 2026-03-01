package com.domenicwalther.brautcloud.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EventRequest {

	private Long userId;

	private String eventName;

	private String location;

	private LocalDateTime date;

	private String password;

	private String qrCode;

}