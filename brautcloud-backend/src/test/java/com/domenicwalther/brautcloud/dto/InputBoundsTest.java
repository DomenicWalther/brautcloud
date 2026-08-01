package com.domenicwalther.brautcloud.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InputBoundsTest {

	private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

	@Test
	void authenticationFieldsRejectOversizedEmailAndPassword() {
		AuthRequest request = new AuthRequest("a".repeat(250) + "@example.com", "p".repeat(73));

		assertThat(validator.validate(request)).extracting(violation -> violation.getPropertyPath().toString())
			.contains("email", "password");
	}

	@Test
	void eventFieldsRejectOversizedNamesAndGalleryPassword() {
		EventRequest request = new EventRequest();
		request.setEventName("x".repeat(256));
		request.setPassword("p".repeat(73));

		assertThat(validator.validate(request)).extracting(violation -> violation.getPropertyPath().toString())
			.contains("eventName", "password");
	}

}
