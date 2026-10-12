package com.ayush.appointments;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

class AuthRateLimitFilter extends OncePerRequestFilter {
  private final AuthRateLimiter limiter;
  private final ObjectMapper mapper;

  AuthRateLimitFilter(AuthRateLimiter limiter, ObjectMapper mapper) {
    this.limiter = limiter;
    this.mapper = mapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getServletPath();
    return !"POST".equals(request.getMethod())
        || !("/api/auth/login".equals(path) || "/api/auth/register".equals(path));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    AuthRateLimiter.Attempt attempt;
    try {
      attempt = limiter.record(request.getRemoteAddr());
    } catch (DataAccessException e) {
      HttpProblems.write(
          mapper,
          response,
          HttpStatus.SERVICE_UNAVAILABLE,
          "Authentication is temporarily unavailable");
      return;
    }
    if (!attempt.allowed()) {
      response.setHeader("Retry-After", Long.toString(attempt.retryAfterSeconds()));
      HttpProblems.write(
          mapper,
          response,
          HttpStatus.TOO_MANY_REQUESTS,
          "Too many authentication attempts; retry later");
      return;
    }
    chain.doFilter(request, response);
  }
}
