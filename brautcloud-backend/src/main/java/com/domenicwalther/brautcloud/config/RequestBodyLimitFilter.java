package com.domenicwalther.brautcloud.config;

import com.domenicwalther.brautcloud.exception.PayloadTooLargeException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Component
public class RequestBodyLimitFilter extends OncePerRequestFilter {

	private final long maxBodyBytes;

	public RequestBodyLimitFilter(@Value("${app.request.max-body-bytes:1048576}") long maxBodyBytes) {
		this.maxBodyBytes = maxBodyBytes;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		if (!isJsonRequest(request)) {
			filterChain.doFilter(request, response);
			return;
		}
		if (request.getContentLengthLong() > maxBodyBytes) {
			response.sendError(HttpStatus.PAYLOAD_TOO_LARGE.value(), "Request body exceeds the 1 MB limit");
			return;
		}
		filterChain.doFilter(new LimitedBodyRequest(request, maxBodyBytes), response);
	}

	private boolean isJsonRequest(HttpServletRequest request) {
		String contentType = request.getContentType();
		return contentType != null && contentType.toLowerCase().startsWith("application/json");
	}

	private static final class LimitedBodyRequest extends HttpServletRequestWrapper {

		private final long maxBytes;

		private LimitedBodyRequest(HttpServletRequest request, long maxBytes) {
			super(request);
			this.maxBytes = maxBytes;
		}

		@Override
		public ServletInputStream getInputStream() throws IOException {
			return new LimitedServletInputStream(super.getInputStream(), maxBytes);
		}

		@Override
		public BufferedReader getReader() throws IOException {
			return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
		}

	}

	private static final class LimitedServletInputStream extends ServletInputStream {

		private final ServletInputStream delegate;

		private final long maxBytes;

		private long bytesRead;

		private LimitedServletInputStream(ServletInputStream delegate, long maxBytes) {
			this.delegate = delegate;
			this.maxBytes = maxBytes;
		}

		@Override
		public int read() throws IOException {
			int value = delegate.read();
			if (value >= 0) {
				increment(1);
			}
			return value;
		}

		@Override
		public int read(byte[] buffer, int offset, int length) throws IOException {
			int value = delegate.read(buffer, offset, length);
			if (value > 0) {
				increment(value);
			}
			return value;
		}

		private void increment(int count) {
			bytesRead += count;
			if (bytesRead > maxBytes) {
				throw new PayloadTooLargeException("Request body exceeds the 1 MB limit");
			}
		}

		@Override
		public boolean isFinished() {
			return delegate.isFinished();
		}

		@Override
		public boolean isReady() {
			return delegate.isReady();
		}

		@Override
		public void setReadListener(jakarta.servlet.ReadListener listener) {
			delegate.setReadListener(listener);
		}

	}

}
