package com.domenicwalther.brautcloud;

import com.domenicwalther.brautcloud.support.FullStackIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationStartupIntegrationTest extends FullStackIntegrationTest {

	private static final List<String> EXPECTED_MIGRATION_VERSIONS = List.of("1", "2", "3", "4", "5", "6", "7", "8", "9",
			"10", "11", "12", "13");

	@Autowired
	private ApplicationContext applicationContext;

	@Autowired
	private Flyway flyway;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void applicationStartsWithEveryMigrationAppliedToPostgres() {
		assertThat(applicationContext.getBean(BrautcloudApplication.class)).isNotNull();
		assertThat(Arrays.stream(flyway.info().applied()).map(info -> info.getVersion().getVersion()))
			.containsExactlyElementsOf(EXPECTED_MIGRATION_VERSIONS);
	}

	@Test
	void migrationVersionsRemainOrderedAndFullyApplied() {
		assertThat(Arrays.stream(flyway.info().all()).map(info -> info.getVersion().getVersion()))
			.containsExactlyElementsOf(EXPECTED_MIGRATION_VERSIONS);
		assertThat(flyway.info().pending()).isEmpty();
	}

	@Test
	void migratedSchemaRetainsProductionRelevantPostgresSemantics() {
		assertThat(columnType("users", "id")).isEqualTo("uuid");
		assertThat(columnType("events", "user_id")).isEqualTo("uuid");
		assertThat(columnType("images", "is_uploaded")).isEqualTo("boolean");
		assertThat(columnType("refresh_tokens", "token_hash")).isEqualTo("character varying");
		assertThat(columnType("users", "token_version")).isEqualTo("integer");
		assertThat(columnType("images", "content_type")).isEqualTo("character varying");
		assertThat(columnType("images", "size_bytes")).isEqualTo("bigint");
		assertThat(columnType("images", "deletion_requested")).isEqualTo("boolean");
		assertThat(columnType("events", "deletion_requested")).isEqualTo("boolean");
		assertThat(columnType("storage_deletion_jobs", "next_attempt_at")).isEqualTo("timestamp without time zone");
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pg_extension WHERE extname = 'uuid-ossp'",
				Integer.class))
			.isOne();
	}

	private String columnType(String table, String column) {
		return jdbcTemplate.queryForObject(
				"SELECT data_type FROM information_schema.columns WHERE table_schema = 'public' AND table_name = ? AND column_name = ?",
				String.class, table, column);
	}

}
