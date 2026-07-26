package com.domenicwalther.brautcloud.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record OnboardingRequest(
		@NotBlank(message = "First name is required") @Size(max = 100,
				message = "First name must not exceed 100 characters") String firstName,
		@NotBlank(message = "Partner's first name is required") @Size(max = 100,
				message = "Partner's first name must not exceed 100 characters") String partnerFirstName,
		@NotBlank(message = "Family name is required") @Size(max = 255,
				message = "Family name must not exceed 255 characters") String familyName,
		@NotBlank(message = "Venue is required") @Size(max = 255,
				message = "Venue must not exceed 255 characters") String venue,
		@NotNull(message = "Event date is required") LocalDateTime date) {
}
