package com.domenicwalther.brautcloud.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AuthRequest(@NotBlank(message = "Email is required") @Email(message = "Email is invalid") String email,
		@NotBlank(message = "Password is required") String password) {
}
