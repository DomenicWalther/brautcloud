package com.domenicwalther.brautcloud.service;

import com.domenicwalther.brautcloud.model.Event;
import com.domenicwalther.brautcloud.repository.EventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GalleryPasswordMigrationTest {

	@Mock
	private EventRepository eventRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Test
	void startupMigrationHashesLegacyValuesAndLeavesEncodedValuesUntouched() {
		Event legacy = Event.builder().password("legacy-secret").build();
		Event encoded = Event.builder().password("$2a$already-hashed").build();
		Event open = Event.builder().password(null).build();
		when(eventRepository.findAll()).thenReturn(List.of(legacy, encoded, open));
		when(passwordEncoder.encode("legacy-secret")).thenReturn("$2a$upgraded");
		GalleryPasswordMigration migration = new GalleryPasswordMigration(eventRepository, passwordEncoder);

		migration.migrateLegacyGalleryPasswords();

		assertThat(legacy.getPassword()).isEqualTo("$2a$upgraded");
		assertThat(encoded.getPassword()).isEqualTo("$2a$already-hashed");
		assertThat(open.getPassword()).isNull();
		ArgumentCaptor<List<Event>> saved = ArgumentCaptor.forClass(List.class);
		verify(eventRepository).saveAll(saved.capture());
		assertThat(saved.getValue()).containsExactly(legacy);
	}

}
