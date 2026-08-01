package com.domenicwalther.brautcloud.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Constraint(validatedBy = StrongGalleryPasswordValidator.class)
@Target({ FIELD, PARAMETER, ANNOTATION_TYPE, RECORD_COMPONENT })
@Retention(RUNTIME)
public @interface StrongGalleryPassword {

	String message() default "Gallery password must be at least 12 characters and use at least three character types";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
