package com.domenicwalther.brautcloud.controller;

import com.domenicwalther.brautcloud.dto.OnboardingRequest;
import com.domenicwalther.brautcloud.dto.OnboardingResponse;
import com.domenicwalther.brautcloud.service.OnboardingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/onboarding")
public class OnboardingController {

	private final OnboardingService onboardingService;

	public OnboardingController(OnboardingService onboardingService) {
		this.onboardingService = onboardingService;
	}

	@PostMapping
	public ResponseEntity<OnboardingResponse> complete(@Valid @RequestBody OnboardingRequest request,
			Authentication authentication) {
		return ResponseEntity.ok(onboardingService.complete(authentication.getName(), request));
	}

}
