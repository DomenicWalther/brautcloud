package com.domenicwalther.brautcloud.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.File;
import java.io.InputStream;
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
		try {
			s3Client.deleteObject(request);
		}
		catch (S3Exception ex) {
			// DeleteObject is idempotent; tolerate providers that report an already
			// absent object as 404 so retries can finish database cleanup.
			if (ex.statusCode() != 404) {
				throw ex;
			}
		}
	}

	public InputStream getObject(String key) {
		GetObjectRequest request = GetObjectRequest.builder().bucket(bucketName).key(key).build();
		return s3Client.getObject(request);
	}

	/**
	 * Returns a time-limited signed URL. Bucket privacy is deployment-owned; this service
	 * never emits an unsigned object URL.
	 */
	public String getPresignedUrl(String key) {
		GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
			.signatureDuration(Duration.ofHours(2))
			.getObjectRequest(r -> r.bucket(bucketName).key(key))
			.build();

		return presigner.presignGetObject(presignRequest).url().toString();
	}

	/**
	 * Returns a time-limited signed upload URL for a server-generated key.
	 */
	public String getPresignedPutUrl(String key) {
		PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
			.signatureDuration(Duration.ofMinutes(15))
			.putObjectRequest(r -> r.bucket(bucketName).key(key))
			.build();

		return presigner.presignPutObject(presignRequest).url().toString();
	}

}
