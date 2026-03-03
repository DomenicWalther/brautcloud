package com.domenicwalther.brautcloud.controller;

import com.domenicwalther.brautcloud.dto.UserResponse;
import com.domenicwalther.brautcloud.model.User;
import com.domenicwalther.brautcloud.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping
	public ResponseEntity<UserResponse> getUser() {
		String email = SecurityContextHolder.getContext().getAuthentication().getName();
		User user = userService.findByEmail(email);
		return ResponseEntity.ok(UserResponse.fromUser(user));
	}

}
