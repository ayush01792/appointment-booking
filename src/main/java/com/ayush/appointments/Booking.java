package com.ayush.appointments;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bookings")
class Booking {
  @Id UUID id;

  @Column(name = "slot_id", nullable = false)
  UUID slotId;

  @Column(name = "customer_id", nullable = false)
  UUID customerId;

  @Column(name = "created_at", nullable = false)
  Instant createdAt;

  @Column(name = "cancelled_at")
  Instant cancelledAt;

  Booking(UUID slotId, UUID customerId, Instant now) {
    id = UUID.randomUUID();
    this.slotId = slotId;
    this.customerId = customerId;
    createdAt = now;
  }

  protected Booking() {}
}
