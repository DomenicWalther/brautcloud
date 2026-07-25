package com.domenicwalther.brautcloud.repository;

import com.domenicwalther.brautcloud.model.Event;
import com.domenicwalther.brautcloud.model.Image;
import com.domenicwalther.brautcloud.model.RefreshToken;
import com.domenicwalther.brautcloud.model.User;
import com.domenicwalther.brautcloud.support.PostgresIntegrationTest;
import com.domenicwalther.brautcloud.support.TestFixtures;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class PersistenceIntegrationTest extends PostgresIntegrationTest {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private EventRepository eventRepository;

	@Autowired
	private ImageRepository imageRepository;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void isolateRepositoryTestData() {
		jdbcTemplate.execute("TRUNCATE TABLE users CASCADE");
		entityManager.clear();
	}

	@Test
	void postgresGeneratesUuidAndAuditDefaultsForPersistedGraph() {
		User user = userRepository.saveAndFlush(TestFixtures.user("owner@example.com"));
		Event event = eventRepository.saveAndFlush(TestFixtures.event(user, "Wedding"));
		Image image = imageRepository.saveAndFlush(TestFixtures.image(event, "photo.jpg", false));
		entityManager.refresh(user);
		entityManager.refresh(event);
		entityManager.refresh(image);

		assertThat(user.getId()).isNotNull();
		assertThat(event.getId()).isNotNull();
		assertThat(image.getId()).isNotNull();
		assertThat(user.getCreatedAt()).isNotNull();
		assertThat(event.getCreatedAt()).isNotNull();
		assertThat(image.getCreatedAt()).isNotNull();
		assertThat(image.isVisible()).isTrue();
		assertThat(image.isUploaded()).isFalse();
	}

	@Test
	void postgresUniqueConstraintRejectsDuplicateEmail() {
		userRepository.saveAndFlush(TestFixtures.user("duplicate@example.com"));

		assertThatThrownBy(() -> userRepository.saveAndFlush(TestFixtures.user("duplicate@example.com")))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void imageQueriesOnlyReturnUploadedImagesForRequestedEvent() {
		User user = userRepository.saveAndFlush(TestFixtures.user("owner@example.com"));
		Event requestedEvent = eventRepository.saveAndFlush(TestFixtures.event(user, "Requested"));
		Event otherEvent = eventRepository.saveAndFlush(TestFixtures.event(user, "Other"));
		Image uploaded = imageRepository.saveAndFlush(TestFixtures.image(requestedEvent, "uploaded.jpg", true));
		imageRepository.saveAndFlush(TestFixtures.image(requestedEvent, "pending.jpg", false));
		imageRepository.saveAndFlush(TestFixtures.image(otherEvent, "other.jpg", true));

		assertThat(imageRepository.findByEventIdAndIsUploadedTrue(requestedEvent.getId())).extracting(Image::getId)
			.containsExactly(uploaded.getId());
	}

	@Test
	void stalePendingImageQueryUsesPostgresTimestampsAndUploadState() {
		User user = userRepository.saveAndFlush(TestFixtures.user("owner@example.com"));
		Event event = eventRepository.saveAndFlush(TestFixtures.event(user, "Wedding"));
		Image stalePending = imageRepository.saveAndFlush(TestFixtures.image(event, "stale.jpg", false));
		Image freshPending = imageRepository.saveAndFlush(TestFixtures.image(event, "fresh.jpg", false));
		Image staleUploaded = imageRepository.saveAndFlush(TestFixtures.image(event, "uploaded.jpg", true));
		jdbcTemplate.update("UPDATE images SET created_at = ? WHERE id IN (?, ?)", LocalDateTime.of(2020, 1, 1, 0, 0),
				stalePending.getId(), staleUploaded.getId());
		entityManager.clear();

		List<Image> stale = imageRepository.findByIsUploadedFalseAndCreatedAtBefore(LocalDateTime.of(2025, 1, 1, 0, 0));

		assertThat(stale).extracting(Image::getId)
			.containsExactly(stalePending.getId())
			.doesNotContain(freshPending.getId(), staleUploaded.getId());
	}

	@Test
	void databaseCascadesUserDeletionThroughEventsImagesAndRefreshTokens() {
		User user = userRepository.saveAndFlush(TestFixtures.user("owner@example.com"));
		Event event = eventRepository.saveAndFlush(TestFixtures.event(user, "Wedding"));
		imageRepository.saveAndFlush(TestFixtures.image(event, "photo.jpg", true));
		RefreshToken refreshToken = new RefreshToken();
		refreshToken.setUser(user);
		refreshToken.setToken(UUID.randomUUID().toString());
		refreshToken.setExpiresAt(Instant.parse("2100-01-01T00:00:00Z"));
		refreshTokenRepository.saveAndFlush(refreshToken);

		jdbcTemplate.update("DELETE FROM users WHERE id = ?", user.getId());
		entityManager.clear();

		assertThat(rowCount("users")).isZero();
		assertThat(rowCount("events")).isZero();
		assertThat(rowCount("images")).isZero();
		assertThat(rowCount("refresh_tokens")).isZero();
	}

	private int rowCount(String table) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
	}

}
