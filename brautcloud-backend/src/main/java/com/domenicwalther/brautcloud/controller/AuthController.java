package com.domenicwalther.brautcloud.controller;

import com.domenicwalther.brautcloud.dto.AuthRequest;
import com.domenicwalther.brautcloud.dto.AuthResponse;
import com.domenicwalther.brautcloud.model.RefreshToken;
import com.domenicwalther.brautcloud.model.User;
import com.domenicwalther.brautcloud.repository.RefreshTokenRepository;
import com.domenicwalther.brautcloud.repository.UserRepository;
import com.domenicwalther.brautcloud.service.JwtService;
import com.domenicwalther.brautcloud.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final UserRepository userRepository;

	private final PasswordEncoder passwordEncoder;

	private final AuthenticationManager authenticationManager;

	private final RefreshTokenService refreshTokenService;

	private final JwtService jwtService;

	public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder,
			AuthenticationManager authenticationManager, JwtService jwtService,
			RefreshTokenService refreshTokenService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.authenticationManager = authenticationManager;
		this.jwtService = jwtService;
		this.refreshTokenService = refreshTokenService;
	}

	@PostMapping("/register")
	public ResponseEntity<String> register(@RequestBody AuthRequest request) {
		if (userRepository.findByEmail(request.email()).isPresent()) {
			return ResponseEntity.badRequest().body("Email already used!");
		}

		User user = new User();
		user.setEmail(request.email());
		user.setPassword(passwordEncoder.encode(request.password()));
		userRepository.save(user);

		return ResponseEntity.ok("User registered successfully");
	}

	@PostMapping("/login")
	public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request, HttpServletResponse response) {
		authenticationManager
			.authenticate(new UsernamePasswordAuthenticationToken(request.email(), request.password()));

		User user = userRepository.findByEmail(request.email())
			.orElseThrow(() -> new UsernameNotFoundException("User not found"));

		String accessToken = jwtService.generateToken(request.email());
		RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

		ResponseCookie cookie = ResponseCookie.from("refresh_token", refreshToken.getToken())
			.httpOnly(true)
			.secure(false)
			.sameSite("Strict")
			.path("/api/auth")
			.maxAge(Duration.ofDays(30))
			.build();

		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

		return ResponseEntity.ok(new AuthResponse(accessToken));
	}

	@PostMapping("/refresh")
	public ResponseEntity<AuthResponse> refresh(@CookieValue(name = "refresh_token") String refreshToken,
			HttpServletResponse response) {

		RefreshToken existing = refreshTokenService.validateRefreshToken(refreshToken);

		String newAccessToken = jwtService.generateToken(existing.getUser().getEmail());

		RefreshToken newRefreshToken = refreshTokenService.createRefreshToken(existing.getUser());

		ResponseCookie cookie = ResponseCookie.from("refresh_token", newRefreshToken.getToken())
			.httpOnly(true)
			.secure(false)
			.sameSite("Strict")
			.path("/api/auth")
			.maxAge(Duration.ofDays(30))
			.build();

		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
		return ResponseEntity.ok(new AuthResponse(newAccessToken));
	}

	@PostMapping("/logout")
	public ResponseEntity<String> logout(@CookieValue(name = "refresh_token", required = false) String refreshToken,
			HttpServletResponse response) {

		// Invalidate the refresh token in the DB if it exists
		if (refreshToken != null) {
			refreshTokenService.deleteByToken(refreshToken);
		}

		// Clear the cookie by setting maxAge to 0
		ResponseCookie cookie = ResponseCookie.from("refresh_token", "")
			.httpOnly(true)
			.secure(false) // true in production
			.sameSite("Strict")
			.path("/api/auth")
			.maxAge(0)
			.build();

		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

		return ResponseEntity.ok("Logged out");
	}

}