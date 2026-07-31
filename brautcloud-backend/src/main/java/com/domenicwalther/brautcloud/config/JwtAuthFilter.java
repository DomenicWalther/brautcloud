package com.domenicwalther.brautcloud.config;

import com.domenicwalther.brautcloud.model.User;
import com.domenicwalther.brautcloud.repository.UserRepository;
import com.domenicwalther.brautcloud.service.CustomUserDetailsService;
import com.domenicwalther.brautcloud.service.JwtService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

	private static final Logger logger = LoggerFactory.getLogger(JwtAuthFilter.class);

	private final JwtService jwtService;

	private final CustomUserDetailsService userDetailsService;

	private final UserRepository userRepository;

	public JwtAuthFilter(JwtService jwtService, CustomUserDetailsService userDetailsService,
			ObjectProvider<UserRepository> userRepositoryProvider) {
		this.jwtService = jwtService;
		this.userDetailsService = userDetailsService;
		this.userRepository = userRepositoryProvider.getIfAvailable();
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		final String authHeader = request.getHeader("Authorization");

		if (authHeader == null || !authHeader.startsWith("Bearer ")) {
			filterChain.doFilter(request, response);
			return;
		}

		String token = authHeader.substring(7);
		try {
			String email = jwtService.extractEmail(token);

			if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
				UserDetails userDetails = userDetailsService.loadUserByUsername(email);
				User user = userRepository == null ? null : userRepository.findPreferredByEmail(email).orElse(null);
				int tokenVersion = user == null ? 0 : user.getTokenVersion();

				if (jwtService.isTokenValid(token, userDetails, tokenVersion)
						&& (userRepository == null || user != null)) {
					UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(userDetails,
							null, userDetails.getAuthorities());
					authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
					SecurityContextHolder.getContext().setAuthentication(authToken);
				}
			}
		}
		catch (JwtException | AuthenticationException | IllegalArgumentException ex) {
			logger.debug("Rejected bearer authentication for {} {} ({})", request.getMethod(), request.getRequestURI(),
					ex.getClass().getSimpleName());
		}

		filterChain.doFilter(request, response);
	}

}
