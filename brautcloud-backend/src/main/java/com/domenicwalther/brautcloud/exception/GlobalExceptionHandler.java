package com.domenicwalther.brautcloud.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<Map<String, String>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
		Map<String, String> error = new HashMap<>();
		error.put("error", "Invalid parameter type");
		error.put("parameter", ex.getName());
		error.put("message", "Expected type " + ex.getRequiredType().getSimpleName());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
		Map<String, String> error = new HashMap<>();
		error.put("error", "Validation failed");
		error.put("message", ex.getBindingResult().getAllErrors().getFirst().getDefaultMessage());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
	}

	@ExceptionHandler(BadRequestException.class)
	public ResponseEntity<Map<String, String>> handleBadRequest(BadRequestException ex) {
		Map<String, String> error = new HashMap<>();
		error.put("error", "Bad Request");
		error.put("message", ex.getMessage());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
		Map<String, String> error = new HashMap<>();
		error.put("error", "Invalid argument");
		error.put("message", ex.getMessage());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
	}

	@ExceptionHandler(BadCredentialsException.class)
	public ResponseEntity<Map<String, String>> handleBadCredentials(BadCredentialsException ex) {
		Map<String, String> error = new HashMap<>();
		error.put("error", "Unauthorized");
		error.put("message", "Invalid email or password");
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
	}

	@ExceptionHandler(InvalidRefreshTokenException.class)
	public ResponseEntity<Map<String, String>> handleInvalidRefreshToken(InvalidRefreshTokenException ex) {
		Map<String, String> error = new HashMap<>();
		error.put("error", "Unauthorized");
		error.put("message", ex.getMessage());
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
	}

	@ExceptionHandler(GalleryPasswordRequiredException.class)
	public ResponseEntity<Map<String, String>> handleGalleryPasswordRequired(GalleryPasswordRequiredException ex) {
		Map<String, String> error = new HashMap<>();
		error.put("message", ex.getMessage());
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<Map<String, String>> handleResourceNotFound(ResourceNotFoundException ex) {
		Map<String, String> error = new HashMap<>();
		error.put("error", "Not Found");
		error.put("message", ex.getMessage());
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<Map<String, String>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
		Map<String, String> error = new HashMap<>();
		error.put("error", "Unsupported Media Type");
		error.put("message", ex.getMessage());
		return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(error);
	}

}
