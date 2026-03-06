package com.domenicwalther.brautcloud.repository;

import com.domenicwalther.brautcloud.dto.ImageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.domenicwalther.brautcloud.model.Image;

import java.util.List;

public interface ImageRepository extends JpaRepository<Image, Long> {

	Page<Image> findByEventId(Long eventId, Pageable pageable);

}
