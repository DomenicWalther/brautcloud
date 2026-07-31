package com.domenicwalther.brautcloud.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class OriginProtectionFilter extends OncePerRequestFilter {

	private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

	private final Set<String> allowedOrigins;

	public OriginProtectionFilter(@Value("${app.security.allowed-origins:http://localhost:4200}") String origins) {
		this.allowedOrigins = Arrays.stream(origins.split(","))
			.map(String::trim)
			.filter(origin -> !origin.isEmpty())
			.map(OriginProtectionFilter::normalizeOrigin)
			.collect(Collectors.toUnmodifiableSet());
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		if (!requiresOriginCheck(request) || isAllowedOrigin(request)) {
			filterChain.doFilter(request, response);
			return;
		}

		response.sendError(HttpServletResponse.SC_FORBIDDEN, "Request origin is not allowed");
	}

	private boolean requiresOriginCheck(HttpServletRequest request) {
		if (!MUTATING_METHODS.contains(request.getMethod())) {
			return false;
		}

		String path = request.getRequestURI();
		return path.equals("/api/auth/login") || path.equals("/api/auth/register") || path.equals("/api/auth/refresh")
				|| path.equals("/api/auth/logout") || path.matches("/api/events/[^/]+/public/view")
				|| path.matches("/api/events/[^/]+/public/images(?:/presigned-url|/uploaded)?")
				|| path.matches("/api/events/[^/]+/public/images/[^/]+");
	}

	private boolean isAllowedOrigin(HttpServletRequest request) {
		String origin = request.getHeader("Origin");
		if (origin != null) {
			try {
				return allowedOrigins.contains(normalizeOrigin(origin));
			}
			catch (IllegalArgumentException exception) {
				return false;
			}
		}

		String referer = request.getHeader("Referer");
		if (referer != null) {
			try {
				return allowedOrigins.contains(normalizeReferer(referer));
			}
			catch (IllegalArgumentException exception) {
				return false;
			}
		}

		// Non-browser clients do not send Origin/Referer. Cookie SameSite policy still
		// protects browser requests.
		return true;
	}

	private static String normalizeOrigin(String value) {
		try {
			URI uri = new URI(value.trim());
			if (uri.getScheme() == null || uri.getHost() == null || uri.getUserInfo() != null
					|| uri.getPath() != null && !uri.getPath().isEmpty() && !uri.getPath().equals("/")
					|| uri.getQuery() != null || uri.getFragment() != null) {
				throw new IllegalArgumentException("Origin must contain scheme and host only");
			}
			int port = uri.getPort();
			if ((uri.getScheme().equalsIgnoreCase("http") && port == 80)
					|| (uri.getScheme().equalsIgnoreCase("https") && port == 443)) {
				port = -1;
			}
			return uri.getScheme().toLowerCase() + "://" + uri.getHost().toLowerCase() + (port == -1 ? "" : ":" + port);
		}
		catch (URISyntaxException exception) {
			throw new IllegalArgumentException("Invalid origin", exception);
		}
	}

	private static String normalizeReferer(String value) {
		try {
			URI uri = new URI(value.trim());
			if (uri.getScheme() == null || uri.getHost() == null) {
				throw new IllegalArgumentException("Referer must contain scheme and host");
			}
			int port = uri.getPort();
			if ((uri.getScheme().equalsIgnoreCase("http") && port == 80)
					|| (uri.getScheme().equalsIgnoreCase("https") && port == 443)) {
				port = -1;
			}
			return uri.getScheme().toLowerCase() + "://" + uri.getHost().toLowerCase() + (port == -1 ? "" : ":" + port);
		}
		catch (URISyntaxException exception) {
			throw new IllegalArgumentException("Invalid referer", exception);
		}
	}

}
