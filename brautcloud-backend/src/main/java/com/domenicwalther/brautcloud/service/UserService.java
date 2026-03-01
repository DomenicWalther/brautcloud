package com.domenicwalther.brautcloud.service;

import com.domenicwalther.brautcloud.dto.UserResponse;
import com.domenicwalther.brautcloud.model.User;
import com.domenicwalther.brautcloud.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

	private final UserRepository userRepository;

	public UserService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	public List<UserResponse> allUsers() {
		return userRepository.findAll().stream().map(UserResponse::fromUser).toList();
	}

}
