package com.domenicwalther.brautcloud.dto;

import java.util.UUID;

/**
 * Read model for image listing and export. Excludes image columns not needed by either
 * operation.
 */
public record EventImageSummary(UUID id, String imageKey, String guestSessionHash) {
}
