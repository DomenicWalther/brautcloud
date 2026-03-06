package com.domenicwalther.brautcloud.service;

import com.domenicwalther.brautcloud.dto.EventImageDTO;
import com.domenicwalther.brautcloud.dto.EventRequest;
import com.domenicwalther.brautcloud.dto.EventResponse;
import com.domenicwalther.brautcloud.dto.ImageResponse;
import com.domenicwalther.brautcloud.model.Event;
import com.domenicwalther.brautcloud.model.Image;
import com.domenicwalther.brautcloud.model.User;
import com.domenicwalther.brautcloud.repository.EventRepository;
import com.domenicwalther.brautcloud.repository.ImageRepository;
import com.domenicwalther.brautcloud.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EventService {

	@Autowired
	private S3Service s3Service;

	private final EventRepository eventRepository;

	private final UserRepository userRepository;

	private final ImageRepository imageRepository;

	public EventService(EventRepository eventRepository, UserRepository userRepository,
			ImageRepository imageRepository) {
		this.eventRepository = eventRepository;
		this.userRepository = userRepository;
		this.imageRepository = imageRepository;
	}

	public List<EventResponse> getEvents() {
		return eventRepository.findAll().stream().map(EventResponse::fromEvent).toList();
	}

	public void addEvent(EventRequest request) {
		User user = userRepository.findById(request.getUserId())
			.orElseThrow(() -> new RuntimeException("User not found"));
		Event event = new Event();
		event.setEventName(request.getEventName());
		event.setLocation(request.getLocation());
		event.setDate(request.getDate());
		event.setPassword(request.getPassword());
		event.setQrCode(request.getQrCode());
		event.setUser(user);
		eventRepository.save(event);
	}

	public void deleteEvent(Long eventID) {
		eventRepository.deleteById(eventID);
	}

	public ResponseEntity<Page<EventImageDTO>> getEventImages(Long eventID, int page, int size) {
		Page<Image> images = imageRepository.findByEventId(eventID, PageRequest.of(page, size));

		Page<EventImageDTO> result = images.map(image -> {
			String url = s3Service.getPresignedUrl(image.getImageKey());
			return new EventImageDTO(image.getId(), url);
		});

		return ResponseEntity.ok(result);

	}

}
