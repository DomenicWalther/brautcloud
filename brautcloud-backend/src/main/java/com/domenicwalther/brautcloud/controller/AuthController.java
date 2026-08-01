package com.domenicwalther.brautcloud.controller;

import com.domenicwalther.brautcloud.dto.AuthRequest;
import com.domenicwalther.brautcloud.dto.AuthResponse;
import com.domenicwalther.brautcloud.exception.BadRequestException;
import com.domenicwalther.brautcloud.model.RefreshToken;
import com.domenicwalther.brautcloud.model.User;
import com.domenicwalther.brautcloud.repository.UserRepository;
import com.domenicwalther.brautcloud.service.AuthSessionService;
import com.domenicwalther.brautcloud.service.AuthenticationRateLimiter;
import com.domenicwalther.brautcloud.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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

	private final AuthenticationRateLimiter authenticationRateLimiter;

	@Autowired
	public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder,
			AuthenticationManager authenticationManager, RefreshTokenService refreshTokenService,
			AuthSessionService authSessionService, AuthenticationRateLimiter authenticationRateLimiter) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.authenticationManager = authenticationManager;
		this.refreshTokenService = refreshTokenService;
		this.authSessionService = authSessionService;
		this.authenticationRateLimiter = authenticationRateLimiter;
	}

	public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder,
			AuthenticationManager authenticationManager, RefreshTokenService refreshTokenService,
			AuthSessionService authSessionService) {
		this(userRepository, passwordEncoder, authenticationManager, refreshTokenService, authSessionService,
				new AuthenticationRateLimiter());
	}

	@PostMapping("/register")
	@Transactional
	public ResponseEntity<AuthResponse> register(@Valid @RequestBody AuthRequest request,
			HttpServletRequest servletRequest, HttpServletResponse response) {
		String submittedEmail = sanitizeEmail(request.email());
		authenticationRateLimiter.checkRegistration(clientAddress(servletRequest), submittedEmail);
		if (request.password().length() < 8) {
			throw new BadRequestException("Unable to complete registration");
		}
		String email = canonicalizeEmail(submittedEmail);
		if (userRepository.existsByEmailCaseInsensitive(submittedEmail)) {
			throw new BadRequestException("Unable to complete registration");
		}
		User user = new User();
		user.setEmail(email);
		user.setPassword(passwordEncoder.encode(request.password()));

		try {
			user = userRepository.saveAndFlush(user);
		}
		catch (DataIntegrityViolationException exception) {
			throw new BadRequestException("Unable to complete registration");
		}

		return ResponseEntity.ok(authSessionService.issue(user, response));
	}

	@PostMapping("/login")
	public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request,
			HttpServletRequest servletRequest, HttpServletResponse response) {
		String email = sanitizeEmail(request.email());
		String clientAddress = clientAddress(servletRequest);
		authenticationRateLimiter.checkLogin(clientAddress, email);
		try {
			Authentication authentication = authenticationManager
				.authenticate(new UsernamePasswordAuthenticationToken(email, request.password()));
			String persistedEmail = ((UserDetails) authentication.getPrincipal()).getUsername();

			User user = userRepository.findByEmail(persistedEmail)
				.orElseThrow(() -> new AuthenticationCredentialsNotFoundException("User not found"));
			authenticationRateLimiter.recordLoginSuccess(clientAddress, email);
			return ResponseEntity.ok(authSessionService.issue(user, response));
		}
		catch (AuthenticationException exception) {
			authenticationRateLimiter.recordLoginFailure(clientAddress, email);
			throw exception;
		}
	}

	@PostMapping("/refresh")
	public ResponseEntity<AuthResponse> refresh(@CookieValue(name = "refresh_token") String refreshToken,
			HttpServletResponse response) {
		RefreshToken rotated = refreshTokenService.rotate(refreshToken);
		return ResponseEntity.ok(authSessionService.issue(rotated.getUser(), rotated, response));
	}

	@PostMapping("/logout")
	public ResponseEntity<String> logout(@CookieValue(name = "refresh_token", required = false) String refreshToken,
			HttpServletResponse response) {
		boolean sessionRevoked = refreshToken != null && refreshTokenService.deleteByToken(refreshToken);
		if (!sessionRevoked) {
			Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
			if (authentication != null && authentication.isAuthenticated()
					&& authentication.getPrincipal() instanceof UserDetails userDetails) {
				refreshTokenService.revokeAccessTokens(userDetails.getUsername());
			}
		}

		ResponseCookie cookie = authSessionService.expiredRefreshCookie();
		if (cookie == null) {
			cookie = ResponseCookie.from("refresh_token", "")
				.httpOnly(true)
				.secure(true)
				.sameSite("Strict")
				.path("/api/auth")
				.maxAge(0)
				.build();
		}
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

		return ResponseEntity.ok("Logged out");
	}

	private String sanitizeEmail(String email) {
		return email.trim();
	}

	private String canonicalizeEmail(String email) {
		return email.toLowerCase(Locale.ROOT);
	}

	private String clientAddress(HttpServletRequest request) {
		String remoteAddress = request.getRemoteAddr();
		return remoteAddress == null ? "unknown" : remoteAddress;
	}

}
