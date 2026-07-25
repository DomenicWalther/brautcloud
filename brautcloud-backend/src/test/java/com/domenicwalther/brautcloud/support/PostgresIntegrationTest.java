package com.domenicwalther.brautcloud.support;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

@ActiveProfiles("test")
public abstract class PostgresIntegrationTest {

	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine")
		.withDatabaseName("brautcloud_test")
		.withUsername("brautcloud")
		.withPassword("brautcloud");

	static {
		POSTGRES.start();
		Runtime.getRuntime().addShutdownHook(new Thread(POSTGRES::stop, "postgres-testcontainer-shutdown"));
	}

	@DynamicPropertySource
	static void configurePostgres(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
	}

}
