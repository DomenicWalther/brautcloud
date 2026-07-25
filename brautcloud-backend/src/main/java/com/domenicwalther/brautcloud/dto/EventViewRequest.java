package com.domenicwalther.brautcloud.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record EventViewRequest(@NotNull(message = "Visitor id is required") UUID visitorId) {
}
