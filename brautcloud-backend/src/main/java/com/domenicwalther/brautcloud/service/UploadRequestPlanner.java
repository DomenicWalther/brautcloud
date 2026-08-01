package com.domenicwalther.brautcloud.service;

import com.domenicwalther.brautcloud.dto.ImageUploadRequest;
import com.domenicwalther.brautcloud.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pure policy for turning an untrusted upload request into storage-ready upload
 * specifications.
 *
 * <p>
 * No authorization, persistence, lifecycle, rate-limit, or storage decisions belong here.
 * </p>
 */
@Component
public final class UploadRequestPlanner {

	public UploadPlan plan(ImageUploadRequest request) {
		List<String> fileNames = request == null ? null : request.getFileNames();
		if (fileNames == null || fileNames.isEmpty() || fileNames.size() > ImageUploadPolicy.MAX_FILES_PER_REQUEST) {
			throw new BadRequestException("At least one valid file name is required");
		}
		List<String> contentTypes = request.getContentTypes();
		if (contentTypes != null && contentTypes.size() != fileNames.size()) {
			throw new BadRequestException("File metadata does not match file names");
		}
		List<Long> fileSizes = request.getFileSizes();
		if (fileSizes != null && fileSizes.size() != fileNames.size()) {
			throw new BadRequestException("File metadata does not match file names");
		}

		Set<String> normalizedNames = new HashSet<>();
		List<UploadSpec> specifications = new ArrayList<>(fileNames.size());
		for (int index = 0; index < fileNames.size(); index++) {
			String fileName = fileNames.get(index);
			if (!isSafeFileName(fileName) || !normalizedNames.add(fileName.toLowerCase(Locale.ROOT))) {
				throw new BadRequestException("At least one valid file name is required");
			}
			String contentType = ImageUploadPolicy.contentTypeForFileName(fileName);
			if (contentType == null) {
				throw new BadRequestException("Only supported image file types are allowed");
			}
			if (contentTypes != null) {
				String requestedType = ImageUploadPolicy.normalizeContentType(contentTypes.get(index));
				if (!contentType.equals(requestedType) || !ImageUploadPolicy.isAllowedContentType(requestedType)) {
					throw new BadRequestException("File content type does not match its name");
				}
			}
			Long sizeBytes = fileSizes == null ? null : fileSizes.get(index);
			if (sizeBytes != null && (sizeBytes <= 0 || sizeBytes > ImageUploadPolicy.MAX_IMAGE_BYTES)) {
				throw new BadRequestException("Image size exceeds the allowed limit");
			}
			specifications.add(new UploadSpec(fileName, sanitizeFileName(fileName), contentType, sizeBytes));
		}
		return new UploadPlan(specifications, contentTypes != null || fileSizes != null);
	}

	private boolean isSafeFileName(String fileName) {
		if (fileName == null || fileName.isBlank() || fileName.length() > 255 || fileName.equals(".")
				|| fileName.equals("..") || fileName.contains("/") || fileName.contains("\\")) {
			return false;
		}
		return fileName.chars().noneMatch(character -> character == 0 || Character.isISOControl(character));
	}

	private String sanitizeFileName(String fileName) {
		return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
	}

	public record UploadPlan(List<UploadSpec> specifications, boolean metadataBound) {

		public UploadPlan {
			specifications = List.copyOf(specifications);
		}
	}

	public record UploadSpec(String fileName, String sanitizedFileName, String contentType, Long sizeBytes) {
	}

}
