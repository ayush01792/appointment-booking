package com.ayush.appointments;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "slots")
class Slot {
  @Id UUID id;

  @Column(name = "staff_id", nullable = false)
  UUID staffId;

  @Column(name = "starts_at", nullable = false)
  Instant startsAt;

  @Column(name = "ends_at", nullable = false)
  Instant endsAt;

  @Column(nullable = false)
  boolean withdrawn;

  Slot(UUID staffId, Instant start) {
    id = UUID.randomUUID();
    this.staffId = staffId;
    startsAt = start;
    endsAt = start.plusSeconds(1800);
  }

  protected Slot() {}
}
