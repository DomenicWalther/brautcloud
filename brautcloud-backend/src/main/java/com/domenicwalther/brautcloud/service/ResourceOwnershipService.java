package com.domenicwalther.brautcloud.service;

import com.domenicwalther.brautcloud.exception.ResourceNotFoundException;
import com.domenicwalther.brautcloud.model.Event;
import com.domenicwalther.brautcloud.model.Image;
import com.domenicwalther.brautcloud.repository.EventRepository;
import com.domenicwalther.brautcloud.repository.ImageRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ResourceOwnershipService {

	private final EventRepository eventRepository;

	private final ImageRepository imageRepository;

	public ResourceOwnershipService(EventRepository eventRepository, ImageRepository imageRepository) {
		this.eventRepository = eventRepository;
		this.imageRepository = imageRepository;
	}

	public Event requireOwnedEvent(String ownerEmail, UUID eventId) {
		Event event = eventRepository.findById(eventId)
			.orElseThrow(() -> new ResourceNotFoundException("Event not found"));
		if (event.getUser() == null || !ownerEmail.equals(event.getUser().getEmail())) {
			throw new ResourceNotFoundException("Event not found");
		}
		return event;
	}

	public Image requireOwnedImage(String ownerEmail, UUID imageId) {
		Image image = imageRepository.findById(imageId)
			.orElseThrow(() -> new ResourceNotFoundException("Image not found"));
		if (image.getEvent() == null || image.getEvent().getUser() == null
				|| !ownerEmail.equals(image.getEvent().getUser().getEmail())) {
			throw new ResourceNotFoundException("Image not found");
		}
		return image;
	}

	public List<Image> requireOwnedImages(String ownerEmail, List<UUID> imageIds) {
		if (imageIds == null || imageIds.isEmpty() || imageIds.stream().anyMatch(id -> id == null)) {
			throw new ResourceNotFoundException("Image not found");
		}
		long distinctCount = imageIds.stream().distinct().count();
		if (distinctCount != imageIds.size()) {
			throw new ResourceNotFoundException("Image not found");
		}

		List<Image> images = imageRepository.findAllById(imageIds);
		if (images.size() != distinctCount || images.stream()
			.anyMatch(image -> image.getEvent() == null || image.getEvent().getUser() == null
					|| !ownerEmail.equals(image.getEvent().getUser().getEmail()))) {
			throw new ResourceNotFoundException("Image not found");
		}
		return images;
	}

}
