package com.domenicwalther.brautcloud.repository;

import com.domenicwalther.brautcloud.model.Image;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ImageRepository extends JpaRepository<Image, UUID> {

	List<Image> findByEventIdAndIsUploadedTrue(UUID eventId);

	List<Image> findByEventId(UUID eventId);

	List<Image> findByDeletionRequestedTrue();

	List<Image> findByIsUploadedFalseAndCreatedAtBefore(LocalDateTime dateTime);

	long countByEventId(UUID eventId);

	long countByEventIdAndGuestSessionHash(UUID eventId, String guestSessionHash);

	@Query("select coalesce(sum(coalesce(i.sizeBytes, 0)), 0) from Image i where i.event.id = :eventId")
	long sumSizeBytesByEventId(@Param("eventId") UUID eventId);

	@Query("select coalesce(sum(coalesce(i.sizeBytes, 0)), 0) from Image i "
			+ "where i.event.id = :eventId and i.guestSessionHash = :guestSessionHash")
	long sumSizeBytesByEventIdAndGuestSessionHash(@Param("eventId") UUID eventId,
			@Param("guestSessionHash") String guestSessionHash);

}
