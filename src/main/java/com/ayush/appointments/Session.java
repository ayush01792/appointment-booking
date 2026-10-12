package com.ayush.appointments;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sessions")
class Session {
  @Id
  @Column(name = "token_hash")
  String tokenHash;

  @Column(name = "account_id", nullable = false)
  UUID accountId;

  @Column(name = "expires_at", nullable = false)
  Instant expiresAt;

  Session(String hash, UUID account, Instant expires) {
    tokenHash = hash;
    accountId = account;
    expiresAt = expires;
  }

  protected Session() {}
}
