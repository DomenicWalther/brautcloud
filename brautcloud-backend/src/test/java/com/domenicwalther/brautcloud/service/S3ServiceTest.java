package com.domenicwalther.brautcloud.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3ServiceTest {

	@Mock
	private S3Client s3Client;

	@Mock
	private S3Presigner presigner;

	private S3Service s3Service;

	@BeforeEach
	void setUp() {
		s3Service = new S3Service();
		ReflectionTestUtils.setField(s3Service, "s3Client", s3Client);
		ReflectionTestUtils.setField(s3Service, "presigner", presigner);
		ReflectionTestUtils.setField(s3Service, "bucketName", "test-bucket");
	}

	@Test
	void verificationRequiresAllowedMetadataAndMatchingImageSignature() {
		byte[] jpeg = new byte[] { (byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00 };
		stubObject("image/jpeg", jpeg);

		assertThat(s3Service.verifyUploadedImage("photo.jpg", "image/jpeg", (long) jpeg.length)).isTrue();
	}

	@Test
	void verificationRejectsSpoofedContentTypeAndOversizedObject() {
		byte[] text = "not an image".getBytes(java.nio.charset.StandardCharsets.UTF_8);
		stubObject("image/jpeg", text);
		assertThat(s3Service.verifyUploadedImage("photo.jpg", "image/jpeg", (long) text.length)).isFalse();

		when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder()
			.contentLength(ImageUploadPolicy.MAX_IMAGE_BYTES + 1)
			.contentType("image/jpeg")
			.build());
		assertThat(s3Service.verifyUploadedImage("photo.jpg", "image/jpeg", null)).isFalse();
	}

	private void stubObject(String contentType, byte[] bytes) {
		when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(
				HeadObjectResponse.builder().contentLength((long) bytes.length).contentType(contentType).build());
		when(s3Client.getObjectAsBytes(any(software.amazon.awssdk.services.s3.model.GetObjectRequest.class)))
			.thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), bytes));
	}

}
