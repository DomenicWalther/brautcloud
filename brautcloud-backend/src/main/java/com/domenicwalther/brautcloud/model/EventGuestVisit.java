package com.domenicwalther.brautcloud.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "event_guest_visits")
public class EventGuestVisit {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne
	@JoinColumn(name = "event_id")
	private Event event;

	private UUID visitorId;

	@Column(insertable = false, updatable = false)
	private LocalDateTime createdAt;

}
