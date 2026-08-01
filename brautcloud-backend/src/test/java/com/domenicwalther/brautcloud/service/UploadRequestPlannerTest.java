package com.domenicwalther.brautcloud.service;

import com.domenicwalther.brautcloud.dto.ImageUploadRequest;
import com.domenicwalther.brautcloud.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadRequestPlannerTest {

	private final UploadRequestPlanner planner = new UploadRequestPlanner();

	@Test
	void plansValidatedFilesWithoutPerformingSideEffects() {
		ImageUploadRequest request = new ImageUploadRequest(UUID.randomUUID(), List.of("guest photo.jpg"),
				List.of("IMAGE/JPEG; charset=binary"), List.of(42L));

		UploadRequestPlanner.UploadPlan plan = planner.plan(request);

		assertThat(plan.metadataBound()).isTrue();
		assertThat(plan.specifications()).singleElement().satisfies(specification -> {
			assertThat(specification.fileName()).isEqualTo("guest photo.jpg");
			assertThat(specification.sanitizedFileName()).isEqualTo("guest_photo.jpg");
			assertThat(specification.contentType()).isEqualTo("image/jpeg");
			assertThat(specification.sizeBytes()).isEqualTo(42L);
		});
	}

	@Test
	void keepsLegacyOptionalMetadataBehaviorInPlan() {
		UploadRequestPlanner.UploadPlan plan = planner
			.plan(new ImageUploadRequest(UUID.randomUUID(), List.of("photo.jpg")));

		assertThat(plan.metadataBound()).isFalse();
		assertThat(plan.specifications()).singleElement()
			.extracting(UploadRequestPlanner.UploadSpec::sizeBytes)
			.isNull();
	}

	@Test
	void distinguishesMetadataShapeErrors() {
		assertThatThrownBy(() -> planner.plan(new ImageUploadRequest(UUID.randomUUID(), List.of("photo.jpg"),
				List.of("image/jpeg", "image/jpeg"), null)))
			.isInstanceOf(BadRequestException.class)
			.hasMessage("File metadata does not match file names");
	}

	@Test
	void distinguishesContentTypeErrors() {
		assertThatThrownBy(() -> planner
			.plan(new ImageUploadRequest(UUID.randomUUID(), List.of("photo.jpg"), List.of("image/png"), null)))
			.isInstanceOf(BadRequestException.class)
			.hasMessage("File content type does not match its name");
	}

	@Test
	void distinguishesUnsupportedFileErrors() {
		assertThatThrownBy(() -> planner.plan(new ImageUploadRequest(UUID.randomUUID(), List.of("photo.txt"))))
			.isInstanceOf(BadRequestException.class)
			.hasMessage("Only supported image file types are allowed");
	}

	@Test
	void distinguishesSizeErrors() {
		assertThatThrownBy(
				() -> planner.plan(new ImageUploadRequest(UUID.randomUUID(), List.of("photo.jpg"), null, List.of(0L))))
			.isInstanceOf(BadRequestException.class)
			.hasMessage("Image size exceeds the allowed limit");
	}

}
