package com.domenicwalther.brautcloud.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

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

	public byte[] getObjectBytes(String key) {
		GetObjectRequest request = GetObjectRequest.builder().bucket(bucketName).key(key).build();
		return s3Client.getObjectAsBytes(request).asByteArray();
	}

	public String getPresignedUrl(String key) {
		GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
			.signatureDuration(Duration.ofHours(2))
			.getObjectRequest(r -> r.bucket(bucketName).key(key))
			.build();

		return presigner.presignGetObject(presignRequest).url().toString();
	}

	public String getPresignedPutUrl(String key) {
		return getPresignedPutUrl(key, ImageUploadPolicy.contentTypeForFileName(key), null);
	}

	public String getPresignedPutUrl(String key, String contentType, Long contentLength) {
		PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
			.signatureDuration(Duration.ofMinutes(15))
			.putObjectRequest(r -> {
				r.bucket(bucketName).key(key).contentType(contentType);
				if (contentLength != null) {
					r.contentLength(contentLength);
				}
			})
			.build();

		return presigner.presignPutObject(presignRequest).url().toString();
	}

	public boolean verifyUploadedImage(String key, String expectedContentType, Long expectedLength) {
		try {
			HeadObjectResponse head = s3Client
				.headObject(HeadObjectRequest.builder().bucket(bucketName).key(key).build());
			long actualLength = head.contentLength() == null ? -1L : head.contentLength();
			String actualContentType = ImageUploadPolicy.normalizeContentType(head.contentType());
			if (actualLength <= 0 || actualLength > ImageUploadPolicy.MAX_IMAGE_BYTES
					|| !ImageUploadPolicy.isAllowedContentType(actualContentType)) {
				return false;
			}
			if (expectedLength != null && expectedLength.longValue() != actualLength) {
				return false;
			}
			if (expectedContentType != null
					&& !ImageUploadPolicy.normalizeContentType(expectedContentType).equals(actualContentType)) {
				return false;
			}

			byte[] bytes = getObjectBytes(key);
			return bytes.length == actualLength && ImageUploadPolicy.hasValidSignature(bytes, actualContentType);
		}
		catch (RuntimeException exception) {
			// Missing objects, storage errors, and malformed metadata are never
			// publishable.
			return false;
		}
	}

}
