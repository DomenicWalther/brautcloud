package com.domenicwalther.brautcloud.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Data
@Table(name = "events")
public class Event {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne
	@JoinColumn(name = "user_id")
	private User user;

	private String eventName;

	private String location;

	private LocalDateTime date;

	private String password;

	private String qrCode;

	@Column(insertable = false, updatable = false)
	private LocalDateTime createdAt;

	@OneToMany(mappedBy = "event")
	private List<Image> images;

}
