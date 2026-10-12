package com.ayush.appointments;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

class BearerTokenFilter extends OncePerRequestFilter {
  private final AuthService auth;
  private final ObjectMapper mapper;

  BearerTokenFilter(AuthService auth, ObjectMapper mapper) {
    this.auth = auth;
    this.mapper = mapper;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String header = req.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      try {
        auth.authenticate(header.substring(7))
            .ifPresent(
                account -> {
                  var authentication =
                      new UsernamePasswordAuthenticationToken(
                          account.id,
                          null,
                          List.of(new SimpleGrantedAuthority("ROLE_" + account.role.name())));
                  SecurityContextHolder.getContext().setAuthentication(authentication);
                });
      } catch (DataAccessException e) {
        HttpProblems.write(
            mapper,
            res,
            HttpStatus.SERVICE_UNAVAILABLE,
            "Authentication is temporarily unavailable");
        return;
      }
    }
    chain.doFilter(req, res);
  }
}
