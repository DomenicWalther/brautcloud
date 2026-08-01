package com.domenicwalther.brautcloud.repository;

import com.domenicwalther.brautcloud.model.StorageDeletionJob;
import com.domenicwalther.brautcloud.model.StorageDeletionResourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

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

	List<StorageDeletionJob> findByLeaseToken(String leaseToken);

	Optional<StorageDeletionJob> findByIdAndLeaseToken(UUID id, String leaseToken);

	@Transactional
	@Modifying
	@Query(value = """
			UPDATE storage_deletion_jobs AS job
			SET lease_token = :leaseToken, lease_until = :leaseUntil
			WHERE job.id IN (
				SELECT candidate.id
				FROM storage_deletion_jobs AS candidate
				WHERE candidate.next_attempt_at <= :now
				  AND (candidate.lease_until IS NULL OR candidate.lease_until <= :now)
				ORDER BY candidate.next_attempt_at, candidate.created_at
				FOR UPDATE SKIP LOCKED
				LIMIT :limit
			)
			""", nativeQuery = true)
	int claimDueJobs(@Param("now") LocalDateTime now, @Param("leaseUntil") LocalDateTime leaseUntil,
			@Param("leaseToken") String leaseToken, @Param("limit") int limit);

	@Transactional
	@Modifying
	@Query(value = """
			UPDATE storage_deletion_jobs
			SET lease_token = :leaseToken, lease_until = :leaseUntil
			WHERE id = :id
			  AND next_attempt_at <= :now
			  AND (lease_until IS NULL OR lease_until <= :now)
			""", nativeQuery = true)
	int claimJob(@Param("id") UUID id, @Param("now") LocalDateTime now, @Param("leaseUntil") LocalDateTime leaseUntil,
			@Param("leaseToken") String leaseToken);

	@Transactional
	@Modifying
	@Query("delete from StorageDeletionJob j where j.id = :id and j.leaseToken = :leaseToken")
	int deleteByIdAndLeaseToken(@Param("id") UUID id, @Param("leaseToken") String leaseToken);

	@Transactional
	@Modifying
	@Query("""
			update StorageDeletionJob j
			set j.attempts = j.attempts + 1,
			    j.lastError = :lastError,
			    j.nextAttemptAt = :nextAttemptAt,
			    j.leaseToken = null,
			    j.leaseUntil = null
			where j.id = :id and j.leaseToken = :leaseToken
			""")
	int recordFailure(@Param("id") UUID id, @Param("leaseToken") String leaseToken,
			@Param("lastError") String lastError, @Param("nextAttemptAt") LocalDateTime nextAttemptAt);

}
