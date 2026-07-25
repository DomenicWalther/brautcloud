package com.domenicwalther.brautcloud.controller;

import com.domenicwalther.brautcloud.dto.UserResponse;
import com.domenicwalther.brautcloud.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping
	public ResponseEntity<UserResponse> getUser(@AuthenticationPrincipal UserDetails authenticatedUser) {
		return ResponseEntity.ok(userService.getUserResponse(authenticatedUser.getUsername()));
	}

}
