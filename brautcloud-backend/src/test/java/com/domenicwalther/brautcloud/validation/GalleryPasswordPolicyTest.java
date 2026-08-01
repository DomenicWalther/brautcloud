package com.domenicwalther.brautcloud.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GalleryPasswordPolicyTest {

	@Test
	void acceptsOptionalStrongPassword() {
		assertThat(GalleryPasswordPolicy.isStrongOptional("Gallery-Access9!")).isTrue();
		assertThat(GalleryPasswordPolicy.isStrongOptional(null)).isTrue();
		assertThat(GalleryPasswordPolicy.isStrongOptional("   ")).isTrue();
	}

	@Test
	void rejectsShortAndLowComplexityPasswords() {
		assertThat(GalleryPasswordPolicy.isStrongOptional("secret")).isFalse();
		assertThat(GalleryPasswordPolicy.isStrongOptional("onlylowercase12")).isFalse();
		assertThatThrownBy(() -> GalleryPasswordPolicy.validateOptional("secret"))
			.isInstanceOf(com.domenicwalther.brautcloud.exception.BadRequestException.class)
			.hasMessageContaining("at least 12 characters");
	}

}
