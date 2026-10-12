package com.ayush.appointments;

import java.time.Clock;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
class AuthRateLimiter {
  private final JdbcTemplate jdbc;
  private final Clock clock;
  private final int limit;

  AuthRateLimiter(
      JdbcTemplate jdbc, Clock clock, @Value("${app.auth.requests-per-minute:20}") int limit) {
    if (limit < 1) throw new IllegalArgumentException("Auth request limit must be positive");
    this.jdbc = jdbc;
    this.clock = clock;
    this.limit = limit;
  }

  record Attempt(boolean allowed, long retryAfterSeconds) {}

  Attempt record(String clientAddress) {
    long now = clock.instant().getEpochSecond();
    long window = Math.floorDiv(now, 60) * 60;
    // PostgreSQL increments atomically: parallel requests cannot all observe an old counter.
    Long count =
        jdbc.queryForObject(
            """
            INSERT INTO auth_rate_limits(client_hash, window_start, requests) VALUES (?, ?, 1)
            ON CONFLICT (client_hash, window_start)
            DO UPDATE SET requests = auth_rate_limits.requests + 1 RETURNING requests
            """,
            Long.class,
            AuthService.hash(clientAddress),
            window);
    return new Attempt(Objects.requireNonNull(count) <= limit, window + 60 - now);
  }
}
