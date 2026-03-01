package com.domenicwalther.brautcloud.controller;

import com.domenicwalther.brautcloud.dto.UserResponse;
import com.domenicwalther.brautcloud.model.User;
import com.domenicwalther.brautcloud.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping
	public List<UserResponse> getUsers() {
		return userService.allUsers();
	}

}
