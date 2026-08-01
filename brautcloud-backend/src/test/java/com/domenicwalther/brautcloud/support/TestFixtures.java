package com.domenicwalther.brautcloud.support;

import com.domenicwalther.brautcloud.model.Event;
import com.domenicwalther.brautcloud.model.Image;
import com.domenicwalther.brautcloud.model.User;

import java.time.LocalDateTime;

public final class TestFixtures {

	private TestFixtures() {
	}

	public static User user(String email) {
		return User.builder().email(email).password("encoded-password").build();
	}

	public static Event event(User user, String name) {
		return Event.builder()
			.user(user)
			.eventName(name)
			.firstNameCoupleOne("Alex")
			.firstNameCoupleTwo("Sam")
			.lastName("Cloud")
			.location("Berlin")
			.date(LocalDateTime.of(2030, 6, 15, 14, 0))
			.build();
	}

	public static Image image(Event event, String key, boolean uploaded) {
		Image image = new Image();
		image.setEvent(event);
		image.setImageKey(key);
		image.setVisible(true);
		image.setSizeBytes(4L);
		image.setUploaded(uploaded);
		return image;
	}

}
