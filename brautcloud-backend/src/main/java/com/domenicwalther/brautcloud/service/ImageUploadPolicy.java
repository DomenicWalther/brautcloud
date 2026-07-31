package com.domenicwalther.brautcloud.service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

public final class ImageUploadPolicy {

	public static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;

	public static final int MAX_FILES_PER_REQUEST = 100;

	public static final long MAX_IMAGES_PER_EVENT = 1_000;

	public static final long MAX_IMAGES_PER_GUEST_SESSION = 100;

	public static final long MAX_EVENT_BYTES = 10L * 1024 * 1024 * 1024;

	public static final long MAX_GUEST_SESSION_BYTES = 1L * 1024 * 1024 * 1024;

	private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif",
			"image/heic", "image/heif");

	private ImageUploadPolicy() {
	}

	public static String contentTypeForFileName(String fileName) {
		if (fileName == null) {
			return null;
		}
		int extensionStart = fileName.lastIndexOf('.');
		if (extensionStart < 0 || extensionStart == fileName.length() - 1) {
			return null;
		}
		return switch (fileName.substring(extensionStart + 1).toLowerCase(Locale.ROOT)) {
			case "jpg", "jpeg" -> "image/jpeg";
			case "png" -> "image/png";
			case "webp" -> "image/webp";
			case "gif" -> "image/gif";
			case "heic" -> "image/heic";
			case "heif" -> "image/heif";
			default -> null;
		};
	}

	public static String normalizeContentType(String contentType) {
		if (contentType == null) {
			return null;
		}
		String normalized = contentType.trim().toLowerCase(Locale.ROOT);
		int parameterStart = normalized.indexOf(';');
		return parameterStart >= 0 ? normalized.substring(0, parameterStart).trim() : normalized;
	}

	public static boolean isAllowedContentType(String contentType) {
		return ALLOWED_TYPES.contains(normalizeContentType(contentType));
	}

	public static boolean hasValidSignature(byte[] bytes, String contentType) {
		if (bytes == null) {
			return false;
		}
		String normalizedType = normalizeContentType(contentType);
		return switch (normalizedType == null ? "" : normalizedType) {
			case "image/jpeg" -> bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8
					&& (bytes[2] & 0xff) == 0xff;
			case "image/png" -> startsWith(bytes, new byte[] { (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a });
			case "image/gif" -> startsWith(bytes, "GIF87a".getBytes(StandardCharsets.US_ASCII))
					|| startsWith(bytes, "GIF89a".getBytes(StandardCharsets.US_ASCII));
			case "image/webp" -> startsWith(bytes, "RIFF".getBytes(StandardCharsets.US_ASCII)) && bytes.length >= 12
					&& startsWith(bytes, 8, "WEBP".getBytes(StandardCharsets.US_ASCII));
			case "image/heic", "image/heif" -> isIsoBaseMediaImage(bytes);
			default -> false;
		};
	}

	private static boolean isIsoBaseMediaImage(byte[] bytes) {
		if (bytes.length < 12 || !startsWith(bytes, 4, "ftyp".getBytes(StandardCharsets.US_ASCII))) {
			return false;
		}
		String brand = new String(bytes, 8, 4, StandardCharsets.US_ASCII).toLowerCase(Locale.ROOT);
		return Set.of("heic", "heix", "hevc", "hevx", "mif1", "msf1").contains(brand);
	}

	private static boolean startsWith(byte[] bytes, byte[] prefix) {
		return startsWith(bytes, 0, prefix);
	}

	private static boolean startsWith(byte[] bytes, int offset, byte[] prefix) {
		if (offset < 0 || bytes.length - offset < prefix.length) {
			return false;
		}
		for (int index = 0; index < prefix.length; index++) {
			if (bytes[offset + index] != prefix[index]) {
				return false;
			}
		}
		return true;
	}

}
