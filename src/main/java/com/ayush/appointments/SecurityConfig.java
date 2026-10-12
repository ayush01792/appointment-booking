package com.ayush.appointments;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  SecurityFilterChain security(
      HttpSecurity http,
      AuthService auth,
      AuthRateLimiter limiter,
      com.fasterxml.jackson.databind.ObjectMapper mapper)
      throws Exception {
    return http.csrf(c -> c.disable())
        .headers(
            h ->
                h.contentSecurityPolicy(
                    c ->
                        c.policyDirectives(
                            "default-src 'self'; script-src 'self'; style-src 'self'"
                                + " 'unsafe-inline'; img-src 'self' data:; connect-src 'self';"
                                + " object-src 'none'; base-uri 'self'; frame-ancestors 'none';"
                                + " form-action 'self'")))
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(
                        org.springframework.http.HttpMethod.GET,
                        "/",
                        "/index.html",
                        "/app.css",
                        "/app.js",
                        "/favicon.svg")
                    .permitAll()
                    .requestMatchers(
                        "/actuator/health",
                        "/actuator/health/**",
                        "/api/auth/register",
                        "/api/auth/login",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html")
                    .permitAll()
                    .requestMatchers("/actuator/**", "/api/staff/**")
                    .hasRole("STAFF")
                    .requestMatchers("/api/bookings/**")
                    .hasRole("CUSTOMER")
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (req, res, ex) ->
                            HttpProblems.write(
                                mapper,
                                res,
                                org.springframework.http.HttpStatus.UNAUTHORIZED,
                                "Authentication required"))
                    .accessDeniedHandler(
                        (req, res, ex) ->
                            HttpProblems.write(
                                mapper,
                                res,
                                org.springframework.http.HttpStatus.FORBIDDEN,
                                "Access denied")))
        .addFilterBefore(
            new AuthRateLimitFilter(limiter, mapper), UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(
            new BearerTokenFilter(auth, mapper), UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
