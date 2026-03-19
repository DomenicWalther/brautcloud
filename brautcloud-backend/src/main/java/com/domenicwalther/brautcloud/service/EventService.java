package com.domenicwalther.brautcloud.service;

import com.domenicwalther.brautcloud.dto.EventImageDTO;
import com.domenicwalther.brautcloud.dto.EventRequest;
import com.domenicwalther.brautcloud.dto.EventResponse;
import com.domenicwalther.brautcloud.exception.ResourceNotFoundException;
import com.domenicwalther.brautcloud.model.Event;
import com.domenicwalther.brautcloud.model.Image;
import com.domenicwalther.brautcloud.model.User;
import com.domenicwalther.brautcloud.repository.EventRepository;
import com.domenicwalther.brautcloud.repository.ImageRepository;
import com.domenicwalther.brautcloud.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

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

	public List<EventResponse> getEventsByUserEmail(String email) {
		User user = userRepository.findByEmail(email)
			.orElseThrow(() -> new ResourceNotFoundException("User not found"));
		return eventRepository.findByUser(user).stream().map(EventResponse::fromEvent).toList();
	}

	public void addEvent(EventRequest request) {
		User user = userRepository.findById(request.getUserId())
			.orElseThrow(() -> new ResourceNotFoundException("User not found"));
		Event event = Event.builder()
			.eventName(request.getEventName())
			.lastName(request.getLastName())
			.firstNameCoupleOne(request.getFirstNameCoupleOne())
			.firstNameCoupleTwo(request.getFirstNameCoupleTwo())
			.location(request.getLocation())
			.date(request.getDate())
			.password(request.getPassword())
			.qrCode(request.getQrCode())
			.user(user)
			.build();
		eventRepository.save(event);
	}

	public void deleteEvent(UUID eventID) {
		eventRepository.deleteById(eventID);
	}

	public List<EventImageDTO> getEventImages(UUID eventID) {
		List<Image> images = imageRepository.findByEventIdAndIsUploadedTrue(eventID);

		return images.stream().map(image -> {
			String url = s3Service.getPresignedUrl(image.getImageKey());
			return new EventImageDTO(image.getId(), url);
		}).toList();
	}

}
