package com.domenicwalther.brautcloud;

import com.domenicwalther.brautcloud.support.FullStackIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationStartupIntegrationTest extends FullStackIntegrationTest {

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
			.containsExactly("1", "2", "3", "4", "5");
	}

	@Test
	void migratedSchemaRetainsProductionRelevantPostgresSemantics() {
		assertThat(columnType("users", "id")).isEqualTo("uuid");
		assertThat(columnType("events", "user_id")).isEqualTo("uuid");
		assertThat(columnType("images", "is_uploaded")).isEqualTo("boolean");
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
