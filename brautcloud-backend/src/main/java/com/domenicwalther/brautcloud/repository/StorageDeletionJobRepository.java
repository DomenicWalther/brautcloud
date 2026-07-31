package com.domenicwalther.brautcloud.repository;

import com.domenicwalther.brautcloud.model.StorageDeletionJob;
import com.domenicwalther.brautcloud.model.StorageDeletionResourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StorageDeletionJobRepository extends JpaRepository<StorageDeletionJob, UUID> {

	Optional<StorageDeletionJob> findByResourceTypeAndResourceId(StorageDeletionResourceType resourceType,
			UUID resourceId);

	List<StorageDeletionJob> findByEventIdAndResourceTypeOrderByCreatedAtAsc(UUID eventId,
			StorageDeletionResourceType resourceType);

	List<StorageDeletionJob> findByNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(LocalDateTime now);

}
