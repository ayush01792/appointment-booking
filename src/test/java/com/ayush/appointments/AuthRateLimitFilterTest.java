package com.ayush.appointments;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.*;

class AuthRateLimitFilterTest {
  @Test
  void databaseFailureFailsClosedWithoutExposingItsDetails() throws Exception {
    var limiter = mock(AuthRateLimiter.class);
    when(limiter.record(anyString()))
        .thenThrow(new DataAccessResourceFailureException("private-database-detail"));
    var request = new MockHttpServletRequest("POST", "/api/auth/login");
    request.setServletPath("/api/auth/login");
    var response = new MockHttpServletResponse();
    var chain = new MockFilterChain();
    new AuthRateLimitFilter(limiter, new ObjectMapper()).doFilter(request, response, chain);
    assertThat(response.getStatus()).isEqualTo(503);
    assertThat(response.getContentAsString()).doesNotContain("private-database-detail");
    assertThat(chain.getRequest()).isNull();
  }

  @Test
  void bearerLookupDatabaseFailureIsSanitized() throws Exception {
    var auth = mock(AuthService.class);
    when(auth.authenticate(anyString()))
        .thenThrow(new DataAccessResourceFailureException("private-database-detail"));
    var request = new MockHttpServletRequest("GET", "/api/bookings");
    request.addHeader("Authorization", "Bearer " + "a".repeat(43));
    var response = new MockHttpServletResponse();
    var chain = new MockFilterChain();
    new BearerTokenFilter(auth, new ObjectMapper()).doFilter(request, response, chain);
    assertThat(response.getStatus()).isEqualTo(503);
    assertThat(response.getContentAsString()).doesNotContain("private-database-detail");
    assertThat(chain.getRequest()).isNull();
  }
}
