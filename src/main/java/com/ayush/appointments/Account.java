package com.ayush.appointments;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "accounts")
class Account {
  @Id UUID id;

  @Column(nullable = false)
  String email;

  @Column(name = "password_hash", nullable = false)
  String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  Role role;

  enum Role {
    CUSTOMER,
    STAFF
  }

  Account(String email, String hash, Role role) {
    this.id = UUID.randomUUID();
    this.email = email;
    this.passwordHash = hash;
    this.role = role;
  }

  protected Account() {}
}
