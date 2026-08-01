package com.domenicwalther.brautcloud.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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
	void presignedPutBindsExactContentLength() throws Exception {
		PresignedPutObjectRequest signed = org.mockito.Mockito.mock(PresignedPutObjectRequest.class);
		when(signed.url()).thenReturn(new java.net.URL("https://uploads.test/photo"));
		when(presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(signed);

		assertThat(s3Service.getPresignedPutUrl("photo.jpg", "image/jpeg", 42L))
			.isEqualTo("https://uploads.test/photo");

		ArgumentCaptor<PutObjectPresignRequest> request = ArgumentCaptor.forClass(PutObjectPresignRequest.class);
		verify(presigner).presignPutObject(request.capture());
		assertThat(request.getValue().putObjectRequest().contentLength()).isEqualTo(42L);
		assertThat(request.getValue().putObjectRequest().contentType()).isEqualTo("image/jpeg");
	}

	@Test
	void verificationRequiresAllowedMetadataAndMatchingImageSignature() {
		byte[] jpeg = new byte[] { (byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00 };
		stubObject("image/jpeg", jpeg);

		assertThat(s3Service.verifyUploadedImage("photo.jpg", "image/jpeg", (long) jpeg.length))
			.isEqualTo(ImageVerificationResult.VALID);
	}

	@Test
	void verificationRejectsSpoofedContentTypeAndOversizedObject() {
		byte[] text = "not an image".getBytes(java.nio.charset.StandardCharsets.UTF_8);
		stubObject("image/jpeg", text);
		assertThat(s3Service.verifyUploadedImage("photo.jpg", "image/jpeg", (long) text.length))
			.isEqualTo(ImageVerificationResult.INVALID);

		when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder()
			.contentLength(ImageUploadPolicy.MAX_IMAGE_BYTES + 1)
			.contentType("image/jpeg")
			.build());
		assertThat(s3Service.verifyUploadedImage("photo.jpg", "image/jpeg", (long) text.length))
			.isEqualTo(ImageVerificationResult.INVALID);
	}

	@Test
	void verificationDistinguishesMissingAndTransientStorageFailures() {
		when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(S3Exception.builder().statusCode(404).build())
			.thenThrow(new IllegalStateException("storage unavailable"));

		assertThat(s3Service.verifyUploadedImage("missing.jpg", "image/jpeg", 4L))
			.isEqualTo(ImageVerificationResult.MISSING);
		assertThat(s3Service.verifyUploadedImage("retry.jpg", "image/jpeg", 4L))
			.isEqualTo(ImageVerificationResult.TRANSIENT_FAILURE);
	}

	@Test
	void verificationRejectsSignedLengthMismatch() {
		when(s3Client.headObject(any(HeadObjectRequest.class)))
			.thenReturn(HeadObjectResponse.builder().contentLength(4L).contentType("image/jpeg").build());

		assertThat(s3Service.verifyUploadedImage("photo.jpg", "image/jpeg", 5L))
			.isEqualTo(ImageVerificationResult.INVALID);
	}

	private void stubObject(String contentType, byte[] bytes) {
		when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(
				HeadObjectResponse.builder().contentLength((long) bytes.length).contentType(contentType).build());
		when(s3Client.getObjectAsBytes(any(software.amazon.awssdk.services.s3.model.GetObjectRequest.class)))
			.thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), bytes));
	}

}
