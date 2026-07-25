package com.domenicwalther.brautcloud.controller;

import com.domenicwalther.brautcloud.dto.AuthRequest;
import com.domenicwalther.brautcloud.dto.AuthResponse;
import com.domenicwalther.brautcloud.exception.BadRequestException;
import com.domenicwalther.brautcloud.model.RefreshToken;
import com.domenicwalther.brautcloud.model.User;
import com.domenicwalther.brautcloud.repository.UserRepository;
import com.domenicwalther.brautcloud.service.AuthSessionService;
import com.domenicwalther.brautcloud.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final UserRepository userRepository;

	private final PasswordEncoder passwordEncoder;

	private final AuthenticationManager authenticationManager;

	private final RefreshTokenService refreshTokenService;

	private final AuthSessionService authSessionService;

	public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder,
			AuthenticationManager authenticationManager, RefreshTokenService refreshTokenService,
			AuthSessionService authSessionService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.authenticationManager = authenticationManager;
		this.refreshTokenService = refreshTokenService;
		this.authSessionService = authSessionService;
	}

	@PostMapping("/register")
	@Transactional
	public ResponseEntity<AuthResponse> register(@Valid @RequestBody AuthRequest request,
			HttpServletResponse response) {
		if (request.password().length() < 8) {
			throw new BadRequestException("Password must be at least 8 characters long");
		}
		String submittedEmail = sanitizeEmail(request.email());
		if (userRepository.existsByEmailCaseInsensitive(submittedEmail)) {
			throw new BadRequestException("Email already used!");
		}
		String email = canonicalizeEmail(submittedEmail);

		User user = new User();
		user.setEmail(email);
		user.setPassword(passwordEncoder.encode(request.password()));

		try {
			user = userRepository.saveAndFlush(user);
		}
		catch (DataIntegrityViolationException exception) {
			throw new BadRequestException("Email already used!");
		}

		return ResponseEntity.ok(authSessionService.issue(user, response));
	}

	@PostMapping("/login")
	public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request, HttpServletResponse response) {
		String email = sanitizeEmail(request.email());
		Authentication authentication = authenticationManager
			.authenticate(new UsernamePasswordAuthenticationToken(email, request.password()));
		String persistedEmail = ((UserDetails) authentication.getPrincipal()).getUsername();

		User user = userRepository.findByEmail(persistedEmail)
			.orElseThrow(() -> new AuthenticationCredentialsNotFoundException("User not found"));

		return ResponseEntity.ok(authSessionService.issue(user, response));
	}

	@PostMapping("/refresh")
	public ResponseEntity<AuthResponse> refresh(@CookieValue(name = "refresh_token") String refreshToken,
			HttpServletResponse response) {
		RefreshToken existing = refreshTokenService.validateRefreshToken(refreshToken);
		return ResponseEntity.ok(authSessionService.issue(existing.getUser(), response));
	}

	@PostMapping("/logout")
	public ResponseEntity<String> logout(@CookieValue(name = "refresh_token", required = false) String refreshToken,
			HttpServletResponse response) {
		if (refreshToken != null) {
			refreshTokenService.deleteByToken(refreshToken);
		}

		ResponseCookie cookie = ResponseCookie.from("refresh_token", "")
			.httpOnly(true)
			.secure(false)
			.sameSite("Strict")
			.path("/api/auth")
			.maxAge(0)
			.build();
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

		return ResponseEntity.ok("Logged out");
	}

	private String sanitizeEmail(String email) {
		return email.trim();
	}

	private String canonicalizeEmail(String email) {
		return email.toLowerCase(Locale.ROOT);
	}

}
