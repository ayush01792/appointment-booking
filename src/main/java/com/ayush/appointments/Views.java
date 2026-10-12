package com.ayush.appointments;

import java.time.Instant;
import java.util.UUID;

class Views {
  record AccountView(UUID id, String email, String role) {
    static AccountView of(Account a) {
      return new AccountView(a.id, a.email, a.role.name());
    }
  }

  record SlotView(UUID id, UUID staffId, Instant startsAt, Instant endsAt, boolean withdrawn) {
    static SlotView of(Slot s) {
      return new SlotView(s.id, s.staffId, s.startsAt, s.endsAt, s.withdrawn);
    }
  }

  record BookingView(
      UUID id,
      UUID slotId,
      Instant createdAt,
      Instant cancelledAt,
      UUID staffId,
      Instant startsAt,
      Instant endsAt) {
    static BookingView of(Booking b, Slot s) {
      return new BookingView(
          b.id, b.slotId, b.createdAt, b.cancelledAt, s.staffId, s.startsAt, s.endsAt);
    }
  }
}
