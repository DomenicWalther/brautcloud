package com.domenicwalther.brautcloud;

import com.domenicwalther.brautcloud.support.PostgresTestSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OnboardingMigrationTest {

	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(PostgresTestSupport.IMAGE);

	@BeforeAll
	static void beforeAll() {
		POSTGRES.start();
	}

	@BeforeEach
	void beforeEach() {
		Flyway.configure()
			.dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
			.locations("classpath:db/migration")
			.cleanDisabled(false)
			.load()
			.clean();
	}

	@AfterAll
	static void afterAll() {
		POSTGRES.stop();
	}

	@Test
	void migrationBackfillsEventOwnersAndLeavesEmptyAccountsIncomplete() throws Exception {
		Flyway.configure()
			.dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
			.locations("classpath:db/migration")
			.target("4")
			.load()
			.migrate();

		UUID eventOwnerId;
		UUID emptyUserId;
		LocalDateTime eventCreatedAt = LocalDateTime.of(2025, 6, 1, 12, 30);
		try (Connection connection = connection()) {
			connection.setAutoCommit(false);
			eventOwnerId = insertUser(connection, "owner@example.com");
			emptyUserId = insertUser(connection, "empty@example.com");
			try (PreparedStatement statement = connection
				.prepareStatement("INSERT INTO events (event_name, user_id, created_at) VALUES (?, ?, ?)")) {
				statement.setString(1, "Existing wedding");
				statement.setObject(2, eventOwnerId);
				statement.setTimestamp(3, Timestamp.valueOf(eventCreatedAt));
				statement.executeUpdate();
			}
			connection.commit();
		}

		Flyway.configure()
			.dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
			.locations("classpath:db/migration")
			.load()
			.migrate();

		try (Connection connection = connection()) {
			assertEquals(eventCreatedAt, onboardingCompletedAt(connection, eventOwnerId));
			assertNull(onboardingCompletedAt(connection, emptyUserId));
		}
	}

	@Test
	void migrationBackfillsUsingEarliestEventWhenUserHasMultipleEvents() throws Exception {
		Flyway.configure()
			.dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
			.locations("classpath:db/migration")
			.target("4")
			.load()
			.migrate();

		UUID eventOwnerId;
		LocalDateTime earliestEventCreatedAt = LocalDateTime.of(2025, 3, 10, 8, 0);
		LocalDateTime laterEventCreatedAt = LocalDateTime.of(2025, 6, 1, 12, 30);
		try (Connection connection = connection()) {
			connection.setAutoCommit(false);
			eventOwnerId = insertUser(connection, "multi-event-owner@example.com");
			try (PreparedStatement statement = connection
				.prepareStatement("INSERT INTO events (event_name, user_id, created_at) VALUES (?, ?, ?)")) {
				statement.setString(1, "Later wedding");
				statement.setObject(2, eventOwnerId);
				statement.setTimestamp(3, Timestamp.valueOf(laterEventCreatedAt));
				statement.executeUpdate();

				statement.setString(1, "Earliest wedding");
				statement.setObject(2, eventOwnerId);
				statement.setTimestamp(3, Timestamp.valueOf(earliestEventCreatedAt));
				statement.executeUpdate();
			}
			connection.commit();
		}

		Flyway.configure()
			.dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
			.locations("classpath:db/migration")
			.load()
			.migrate();

		try (Connection connection = connection()) {
			assertEquals(earliestEventCreatedAt, onboardingCompletedAt(connection, eventOwnerId));
		}
	}

	private static UUID insertUser(Connection connection, String email) throws Exception {
		try (PreparedStatement statement = connection
			.prepareStatement("INSERT INTO users (email, password) VALUES (?, ?) RETURNING id")) {
			statement.setString(1, email);
			statement.setString(2, "encoded-password");
			try (ResultSet result = statement.executeQuery()) {
				result.next();
				return result.getObject(1, UUID.class);
			}
		}
	}

	private static LocalDateTime onboardingCompletedAt(Connection connection, UUID userId) throws Exception {
		try (PreparedStatement statement = connection
			.prepareStatement("SELECT onboarding_completed_at FROM users WHERE id = ?")) {
			statement.setObject(1, userId);
			try (ResultSet result = statement.executeQuery()) {
				result.next();
				Timestamp timestamp = result.getTimestamp(1);
				return timestamp == null ? null : timestamp.toLocalDateTime();
			}
		}
	}

	private static Connection connection() throws Exception {
		return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
	}

}
