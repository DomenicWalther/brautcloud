package com.domenicwalther.brautcloud.controller;

import com.domenicwalther.brautcloud.repository.UserRepository;
import com.domenicwalther.brautcloud.service.JwtService;
import com.domenicwalther.brautcloud.service.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuthControllerTest {

	@Test
	void logoutDeletesRefreshTokenAndExpiresMatchingCookie() {
		RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
		AuthController controller = new AuthController(mock(UserRepository.class), mock(PasswordEncoder.class),
				mock(AuthenticationManager.class), mock(JwtService.class), refreshTokenService);
		MockHttpServletResponse servletResponse = new MockHttpServletResponse();

		var response = controller.logout("stored-refresh-token", servletResponse);

		verify(refreshTokenService).deleteByToken("stored-refresh-token");
		assertEquals(200, response.getStatusCode().value());
		assertEquals("Logged out", response.getBody());

		String setCookie = servletResponse.getHeader(HttpHeaders.SET_COOKIE);
		assertNotNull(setCookie);
		assertTrue(setCookie.startsWith("refresh_token="));
		assertTrue(setCookie.contains("Max-Age=0"));
		assertTrue(setCookie.contains("Path=/api/auth"));
		assertTrue(setCookie.contains("HttpOnly"));
	}

}
