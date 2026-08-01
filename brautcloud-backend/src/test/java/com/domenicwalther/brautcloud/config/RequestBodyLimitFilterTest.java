package com.domenicwalther.brautcloud.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RequestBodyLimitFilterTest {

	@Test
	void rejectsJsonBodyLargerThanOneMegabyteBeforeControllerExecution() throws Exception {
		RequestBodyLimitFilter filter = new RequestBodyLimitFilter(1_048_576);
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setMethod("POST");
		request.setContentType("application/json");
		request.setContent(new byte[1_048_577]);
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = new MockFilterChain();

		filter.doFilter(request, response, chain);

		assertThat(response.getStatus()).isEqualTo(413);
		assertThat(((MockFilterChain) chain).getRequest()).isNull();
	}

}
