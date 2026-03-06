package com.domenicwalther.brautcloud.service;

import org.hibernate.event.spi.DeleteContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.File;
import java.time.Duration;

@Service
public class S3Service {

	@Autowired
	private S3Client s3Client;

	@Autowired
	private S3Presigner presigner;

	@Value("${aws.s3.bucket}")
	private String bucketName;

	public void uploadFile(String key, File file) {
		PutObjectRequest request = PutObjectRequest.builder().bucket(bucketName).key(key).build();

		s3Client.putObject(request, file.toPath());
	}

	public void deleteFile(String key) {
		DeleteObjectRequest request = DeleteObjectRequest.builder().bucket(bucketName).key(key).build();
		s3Client.deleteObject(request);

	}

	public String getPresignedUrl(String key) {
		GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
			.signatureDuration(Duration.ofHours(2))
			.getObjectRequest(r -> r.bucket(bucketName).key(key))
			.build();

		return presigner.presignGetObject(presignRequest).url().toString();
	}

}
