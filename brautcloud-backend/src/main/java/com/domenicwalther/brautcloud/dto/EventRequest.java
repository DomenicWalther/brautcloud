package com.domenicwalther.brautcloud.dto;

import lombok.AllArgsConstructor;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class EventRequest {

	private UUID userId;

	@Size(max = 255, message = "Event name must not exceed 255 characters")
	private String eventName;

	@Size(max = 255, message = "Family name must not exceed 255 characters")
	private String lastName;

	@Size(max = 255, message = "First couple name must not exceed 255 characters")
	private String firstNameCoupleOne;

	@Size(max = 255, message = "Second couple name must not exceed 255 characters")
	private String firstNameCoupleTwo;

	@Size(max = 255, message = "Location must not exceed 255 characters")
	private String location;

	private LocalDateTime date;

	@Size(max = 72, message = "Password must not exceed 72 characters")
	private String password;

	@Size(max = 4096, message = "QR code must not exceed 4096 characters")
	private String qrCode;

}