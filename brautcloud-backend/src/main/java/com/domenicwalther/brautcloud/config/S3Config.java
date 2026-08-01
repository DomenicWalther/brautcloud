package com.domenicwalther.brautcloud.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
public class S3Config {

	private final String accessKeyId;

	private final String secretAccessKey;

	private final String region;

	private final String s3Endpoint;

	private final Environment environment;

	public S3Config(@Value("${aws.accessKeyId}") String accessKeyId,
			@Value("${aws.secretAccessKey}") String secretAccessKey, @Value("${aws.s3.region}") String region,
			@Value("${aws.s3.endpoint}") String s3Endpoint, Environment environment) {
		this.accessKeyId = accessKeyId;
		this.secretAccessKey = secretAccessKey;
		this.region = region;
		this.s3Endpoint = s3Endpoint;
		this.environment = environment;
	}

	@PostConstruct
	void validateEndpoint() {
		URI endpoint = URI.create(this.s3Endpoint);
		boolean localOrTestProfile = this.environment.acceptsProfiles(Profiles.of("local", "test"));
		boolean https = "https".equalsIgnoreCase(endpoint.getScheme());
		boolean localHttp = localOrTestProfile && "http".equalsIgnoreCase(endpoint.getScheme());
		if (!endpoint.isAbsolute() || (!https && !localHttp)) {
			throw new IllegalStateException(
					"S3 endpoint must use HTTPS outside local/test profiles: " + this.s3Endpoint);
		}
	}

	@Bean
	public S3Client s3Client() {
		return S3Client.builder()
			.endpointOverride(endpointUri())
			.region(Region.of(this.region))
			.credentialsProvider(StaticCredentialsProvider
				.create(AwsBasicCredentials.create(this.accessKeyId, this.secretAccessKey)))
			.forcePathStyle(true)
			.build();
	}

	@Bean
	public S3Presigner s3Presigner() {
		return S3Presigner.builder()
			.endpointOverride(endpointUri())
			.credentialsProvider(StaticCredentialsProvider
				.create(AwsBasicCredentials.create(this.accessKeyId, this.secretAccessKey)))
			.region(Region.of(this.region))
			.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
			.build();
	}

	private URI endpointUri() {
		return URI.create(this.s3Endpoint);
	}

}
