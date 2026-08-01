package com.domenicwalther.brautcloud.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import com.domenicwalther.brautcloud.validation.StrongGalleryPassword;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class EventRequest {

	private UUID userId;

	private String eventName;

	private String lastName;

	private String firstNameCoupleOne;

	private String firstNameCoupleTwo;

	private String location;

	private LocalDateTime date;

	@StrongGalleryPassword
	private String password;

	private String qrCode;

}