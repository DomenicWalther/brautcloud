package com.domenicwalther.brautcloud.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OriginProtectionFilterTest {

	private final OriginProtectionFilter filter = new OriginProtectionFilter("http://localhost:4200");

	@Test
	void rejectsUntrustedOriginForCookieBearingAuthMutation() throws Exception {
		MockHttpServletRequest request = request("POST", "/api/auth/refresh", "https://evil.example");
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = mock(FilterChain.class);

		filter.doFilter(request, response, chain);

		assertThat(response.getStatus()).isEqualTo(403);
		verify(chain, org.mockito.Mockito.never()).doFilter(request, response);
	}

	@Test
	void rejectsUntrustedOriginForPublicGuestMutation() throws Exception {
		MockHttpServletRequest request = request("DELETE", "/api/events/123/public/images/456", "https://evil.example");
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = mock(FilterChain.class);

		filter.doFilter(request, response, chain);

		assertThat(response.getStatus()).isEqualTo(403);
		verify(chain, org.mockito.Mockito.never()).doFilter(request, response);
	}

	@Test
	void allowsConfiguredOriginAndAnonymousPublicReads() throws Exception {
		MockHttpServletRequest mutation = request("POST", "/api/events/123/public/images/uploaded",
				"http://localhost:4200");
		MockHttpServletRequest read = request("GET", "/api/events/123/public/images", "https://evil.example");
		MockHttpServletResponse mutationResponse = new MockHttpServletResponse();
		MockHttpServletResponse readResponse = new MockHttpServletResponse();
		FilterChain mutationChain = mock(FilterChain.class);
		FilterChain readChain = mock(FilterChain.class);

		filter.doFilter(mutation, mutationResponse, mutationChain);
		filter.doFilter(read, readResponse, readChain);

		assertThat(mutationResponse.getStatus()).isEqualTo(200);
		assertThat(readResponse.getStatus()).isEqualTo(200);
		verify(mutationChain).doFilter(mutation, mutationResponse);
		verify(readChain).doFilter(read, readResponse);
	}

	private static MockHttpServletRequest request(String method, String path, String origin) {
		MockHttpServletRequest request = new MockHttpServletRequest(method, path);
		request.addHeader("Origin", origin);
		return request;
	}

}
