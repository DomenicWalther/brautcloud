package com.domenicwalther.brautcloud.repository;

import com.domenicwalther.brautcloud.model.Image;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ImageRepository extends JpaRepository<Image, UUID> {

	List<Image> findByEventIdAndIsUploadedTrue(UUID eventId);

	List<Image> findByIsUploadedFalseAndCreatedAtBefore(LocalDateTime dateTime);

}
