package com.domenicwalther.brautcloud.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "images")
public class Image {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne
	@JoinColumn(name = "event_id")
	private Event event;

	private String imageKey;

	private boolean isVisible;

	@Column(insertable = false, updatable = false)
	private LocalDateTime createdAt;

}
