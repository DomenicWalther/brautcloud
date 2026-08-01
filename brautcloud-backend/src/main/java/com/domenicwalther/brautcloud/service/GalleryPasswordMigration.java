package com.domenicwalther.brautcloud.service;

import com.domenicwalther.brautcloud.model.Event;
import com.domenicwalther.brautcloud.repository.EventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** Upgrades gallery passwords from pre-hash releases when application starts. */
@Component
public class GalleryPasswordMigration {

	private static final Logger log = LoggerFactory.getLogger(GalleryPasswordMigration.class);

	private final EventRepository eventRepository;

	private final PasswordEncoder passwordEncoder;

	public GalleryPasswordMigration(EventRepository eventRepository, PasswordEncoder passwordEncoder) {
		this.eventRepository = eventRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@EventListener(ApplicationReadyEvent.class)
	@Transactional
	public void migrateLegacyGalleryPasswords() {
		List<Event> legacyEvents = new ArrayList<>();
		for (Event event : eventRepository.findAll()) {
			String password = event.getPassword();
			if (password != null && !password.isBlank() && !EventService.isEncodedGalleryPassword(password)) {
				event.setPassword(passwordEncoder.encode(password));
				legacyEvents.add(event);
			}
		}
		if (!legacyEvents.isEmpty()) {
			eventRepository.saveAll(legacyEvents);
			log.info("Legacy gallery password migration completed: {} records updated", legacyEvents.size());
		}
	}

}
