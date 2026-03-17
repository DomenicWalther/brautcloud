package com.domenicwalther.brautcloud.repository;

import com.domenicwalther.brautcloud.dto.ImageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.domenicwalther.brautcloud.model.Image;

import java.util.List;
import java.util.UUID;

public interface ImageRepository extends JpaRepository<Image, UUID> {

	Page<Image> findByEventId(UUID eventId, Pageable pageable);

}
