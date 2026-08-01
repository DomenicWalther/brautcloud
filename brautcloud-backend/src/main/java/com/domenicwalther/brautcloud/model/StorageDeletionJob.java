package com.domenicwalther.brautcloud.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "storage_deletion_jobs", uniqueConstraints = @UniqueConstraint(name = "uq_storage_deletion_job_resource",
		columnNames = { "resource_type", "resource_id" }))
public class StorageDeletionJob {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Enumerated(EnumType.STRING)
	@Column(name = "resource_type", nullable = false, length = 16)
	private StorageDeletionResourceType resourceType;

	@Column(name = "resource_id", nullable = false)
	private UUID resourceId;

	@Column(name = "event_id", nullable = false)
	private UUID eventId;

	@Column(name = "object_key")
	private String objectKey;

	@Column(nullable = false)
	private int attempts;

	@Column(nullable = false)
	private LocalDateTime nextAttemptAt;

	@Column(columnDefinition = "TEXT")
	private String lastError;

	@Column(name = "lease_token", length = 64)
	private String leaseToken;

	@Column(name = "lease_until")
	private LocalDateTime leaseUntil;

	@Column(insertable = false, updatable = false)
	private LocalDateTime createdAt;

}
