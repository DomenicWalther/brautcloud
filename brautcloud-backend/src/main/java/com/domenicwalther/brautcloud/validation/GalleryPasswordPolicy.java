package com.domenicwalther.brautcloud.validation;

import com.domenicwalther.brautcloud.exception.BadRequestException;

public final class GalleryPasswordPolicy {

	public static final int MIN_LENGTH = 12;

	public static final int MAX_LENGTH = 72;

	private GalleryPasswordPolicy() {
	}

	public static boolean isStrongOptional(String password) {
		if (password == null || password.isBlank()) {
			return true;
		}
		if (password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
			return false;
		}
		int characterTypes = 0;
		if (password.chars().anyMatch(Character::isLowerCase)) {
			characterTypes++;
		}
		if (password.chars().anyMatch(Character::isUpperCase)) {
			characterTypes++;
		}
		if (password.chars().anyMatch(Character::isDigit)) {
			characterTypes++;
		}
		if (password.chars()
			.anyMatch(character -> !Character.isLetterOrDigit(character) && !Character.isWhitespace(character))) {
			characterTypes++;
		}
		return characterTypes >= 3;
	}

	public static void validateOptional(String password) {
		if (!isStrongOptional(password)) {
			throw new BadRequestException(
					"Gallery password must be at least 12 characters and use at least three character types");
		}
	}

}
