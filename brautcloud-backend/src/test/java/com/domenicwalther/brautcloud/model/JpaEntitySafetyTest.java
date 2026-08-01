package com.domenicwalther.brautcloud.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class JpaEntitySafetyTest {

	@Test
	void relationshipGraphCanBeRenderedWithoutRecursion() {
		User user = User.builder().id(UUID.randomUUID()).email("owner@example.com").build();
		Event event = Event.builder().id(UUID.randomUUID()).eventName("Wedding").user(user).build();
		Image image = new Image();
		image.setId(UUID.randomUUID());
		image.setEvent(event);
		user.setEvents(List.of(event));
		event.setImages(List.of(image));

		assertThatCode(() -> {
			user.toString();
			event.toString();
			image.toString();
		}).doesNotThrowAnyException();
		assertThat(user.toString()).doesNotContain("Event{");
		assertThat(event.toString()).doesNotContain("User{", "Image{");
		assertThat(image.toString()).doesNotContain("Event{");
	}

	@Test
	void equalityUsesOnlyPersistentIdentifier() {
		UUID userId = UUID.randomUUID();
		User firstUser = User.builder().id(userId).email("first@example.com").build();
		User secondUser = User.builder().id(userId).email("second@example.com").build();
		firstUser.setEvents(List.of(Event.builder().eventName("First").build()));
		secondUser.setEvents(List.of(Event.builder().eventName("Second").build()));

		assertThat(firstUser).isEqualTo(secondUser).hasSameHashCodeAs(secondUser);
		assertThat(new User()).isNotEqualTo(new User());

		UUID eventId = UUID.randomUUID();
		Event firstEvent = Event.builder().id(eventId).eventName("First").build();
		Event secondEvent = Event.builder().id(eventId).eventName("Second").build();
		assertThat(firstEvent).isEqualTo(secondEvent).hasSameHashCodeAs(secondEvent);

		UUID imageId = UUID.randomUUID();
		Image firstImage = new Image();
		firstImage.setId(imageId);
		firstImage.setImageKey("first.jpg");
		Image secondImage = new Image();
		secondImage.setId(imageId);
		secondImage.setImageKey("second.jpg");
		assertThat(firstImage).isEqualTo(secondImage).hasSameHashCodeAs(secondImage);
	}

}
