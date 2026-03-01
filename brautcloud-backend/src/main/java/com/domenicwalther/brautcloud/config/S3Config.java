package com.domenicwalther.brautcloud.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

@Configuration
public class S3Config {

	@Value("${aws.accessKeyId}")
	private String accessKeyId;

	@Value("${aws.secretAccessKey}")
	private String secretAccessKey;

	@Value("${aws.s3.region}")
	private String region;

	@Value("${aws.s3.endpoint}")
	private String s3Endpoint;

	@Bean
	public S3Client s3Client() {
		return S3Client.builder()
			.endpointOverride(URI.create(s3Endpoint))
			.region(Region.of(region))
			.credentialsProvider(
					StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
			.forcePathStyle(true)
			.build();
	}

}
