package com.domenicwalther.brautcloud.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.domenicwalther.brautcloud.validation.StrongGalleryPassword;

import java.time.LocalDateTime;

public record EventUpdateRequest(
		@NotBlank(message = "Event name is required") @Size(max = 255,
				message = "Event name must not exceed 255 characters") String eventName,
		@NotBlank(message = "First couple name is required") @Size(max = 255,
				message = "First couple name must not exceed 255 characters") String firstNameCoupleOne,
		@NotBlank(message = "Second couple name is required") @Size(max = 255,
				message = "Second couple name must not exceed 255 characters") String firstNameCoupleTwo,
		@NotBlank(message = "Location is required") @Size(max = 255,
				message = "Location must not exceed 255 characters") String location,
		@NotNull(message = "Event date is required") LocalDateTime date, @StrongGalleryPassword String password) {
}
