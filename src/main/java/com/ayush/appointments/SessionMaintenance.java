package com.ayush.appointments;

import java.sql.Timestamp;
import java.time.Clock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SessionMaintenance {
  private final JdbcTemplate jdbc;
  private final Clock clock;

  SessionMaintenance(JdbcTemplate jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  record Purged(int sessions, int rateWindows) {}

  @Scheduled(
      fixedDelayString = "${app.maintenance.delay-ms:900000}",
      initialDelayString = "${app.maintenance.delay-ms:900000}")
  @Transactional
  public Purged purgeExpired() {
    var now = clock.instant();
    int sessions =
        jdbc.update(
            "DELETE FROM sessions WHERE token_hash IN (SELECT token_hash FROM sessions WHERE"
                + " expires_at<=? LIMIT 1000)",
            Timestamp.from(now));
    int windows =
        jdbc.update(
            "DELETE FROM auth_rate_limits WHERE (client_hash,window_start) IN (SELECT"
                + " client_hash,window_start FROM auth_rate_limits WHERE window_start<? LIMIT"
                + " 1000)",
            Math.floorDiv(now.getEpochSecond(), 60) * 60);
    return new Purged(sessions, windows);
  }
}
