package com.domenicwalther.brautcloud.dto;

import com.domenicwalther.brautcloud.service.ImageUploadPolicy;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class ImageUploadRequest {

	private UUID eventId;

	@Size(min = 1, max = ImageUploadPolicy.MAX_FILES_PER_REQUEST, message = "At most 100 files may be uploaded at once")
	private List<@Size(max = 255, message = "File name must not exceed 255 characters") String> fileNames;

	/**
	 * Optional client metadata. It is treated as untrusted and checked again after
	 * upload.
	 */
	@Size(max = ImageUploadPolicy.MAX_FILES_PER_REQUEST, message = "Too many content types")
	private List<@Size(max = 100, message = "Content type must not exceed 100 characters") String> contentTypes;

	/** Optional expected byte lengths used to bind presigned PUT requests. */
	@Size(max = ImageUploadPolicy.MAX_FILES_PER_REQUEST, message = "Too many file sizes")
	private List<Long> fileSizes;

	public ImageUploadRequest() {
	}

	public ImageUploadRequest(UUID eventId, List<String> fileNames) {
		this(eventId, fileNames, null, null);
	}

	public ImageUploadRequest(UUID eventId, List<String> fileNames, List<String> contentTypes, List<Long> fileSizes) {
		this.eventId = eventId;
		this.fileNames = fileNames;
		this.contentTypes = contentTypes;
		this.fileSizes = fileSizes;
	}

	public UUID getEventId() {
		return eventId;
	}

	public void setEventId(UUID eventId) {
		this.eventId = eventId;
	}

	public List<String> getFileNames() {
		return fileNames;
	}

	public void setFileNames(List<String> fileNames) {
		this.fileNames = fileNames;
	}

	public List<String> getContentTypes() {
		return contentTypes;
	}

	public void setContentTypes(List<String> contentTypes) {
		this.contentTypes = contentTypes;
	}

	public List<Long> getFileSizes() {
		return fileSizes;
	}

	public void setFileSizes(List<Long> fileSizes) {
		this.fileSizes = fileSizes;
	}

}
