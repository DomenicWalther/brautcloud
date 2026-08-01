package com.domenicwalther.brautcloud.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class StrongGalleryPasswordValidator implements ConstraintValidator<StrongGalleryPassword, String> {

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		return GalleryPasswordPolicy.isStrongOptional(value);
	}

}
