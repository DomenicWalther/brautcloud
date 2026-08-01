package com.domenicwalther.brautcloud.controller;

import com.domenicwalther.brautcloud.dto.ImageUploadRequest;
import com.domenicwalther.brautcloud.dto.ImageUploadResponse;
import com.domenicwalther.brautcloud.service.CustomUserDetailsService;
import com.domenicwalther.brautcloud.service.ImageService;
import com.domenicwalther.brautcloud.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ImageController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class ImageControllerWebMvcTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ImageService imageService;

	@MockitoBean
	private JwtService jwtService;

	@MockitoBean
	private CustomUserDetailsService userDetailsService;

	@Test
	@WithMockUser(username = "owner@example.com")
	void uploadUrlsUseAuthenticatedIdentity() throws Exception {
		UUID eventId = UUID.randomUUID();
		UUID imageId = UUID.randomUUID();
		when(imageService.generatePresignedUploadUrls(eq("owner@example.com"),
				argThat((ImageUploadRequest request) -> request.getEventId().equals(eventId)
						&& request.getFileNames().equals(List.of("photo.jpg"))
						&& request.getFileSizes().equals(List.of(5L)))))
			.thenReturn(List.of(new ImageUploadResponse(imageId, "https://uploads.test/photo")));

		mockMvc.perform(post("/api/image/presigned-url").contentType(MediaType.APPLICATION_JSON).content("""
				{
				  "eventId": "%s",
				  "fileNames": ["photo.jpg"],
				  "contentTypes": ["image/jpeg"],
				  "fileSizes": [5]
				}
				""".formatted(eventId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].imageId").value(imageId.toString()));
	}

	@Test
	@WithMockUser(username = "owner@example.com")
	void markUploadedUsesAuthenticatedIdentity() throws Exception {
		UUID imageId = UUID.randomUUID();

		mockMvc
			.perform(post("/api/image/uploaded").contentType(MediaType.APPLICATION_JSON)
				.content("[\"%s\"]".formatted(imageId)))
			.andExpect(status().isNoContent());

		verify(imageService).markImagesAsUploaded("owner@example.com", List.of(imageId));
	}

	@Test
	@WithMockUser(username = "owner@example.com")
	void deleteImageUsesAuthenticatedIdentity() throws Exception {
		UUID imageId = UUID.randomUUID();

		mockMvc.perform(delete("/api/image/{imageId}", imageId)).andExpect(status().isNoContent());

		verify(imageService).deleteImageByImageID("owner@example.com", imageId);
	}

}
