package com.domenicwalther.brautcloud.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class S3ConfigTest {

	@Test
	void acceptsHttpsEndpointWithoutLocalProfile() {
		S3Config config = config("https://s3.example.test", "prod");

		assertThatCode(config::validateEndpoint).doesNotThrowAnyException();
	}

	@Test
	void rejectsHttpEndpointWithoutLocalOrTestProfile() {
		S3Config config = config("http://localhost:9000", "prod");

		assertThatThrownBy(config::validateEndpoint).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("HTTPS outside local/test profiles");
	}

	@Test
	void acceptsHttpEndpointInLocalProfile() {
		S3Config config = config("http://localhost:9000", "local");

		assertThatCode(config::validateEndpoint).doesNotThrowAnyException();
	}

	@Test
	void acceptsHttpEndpointInTestProfile() {
		S3Config config = config("http://127.0.0.1:1", "test");

		assertThatCode(config::validateEndpoint).doesNotThrowAnyException();
	}

	private static S3Config config(String endpoint, String profile) {
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles(profile);
		return new S3Config("access-key", "secret-key", "us-east-1", endpoint, environment);
	}

}
