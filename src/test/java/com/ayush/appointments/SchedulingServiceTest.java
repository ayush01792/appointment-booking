package com.ayush.appointments;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;

class SchedulingServiceTest {
  @Test
  void pastSlotIsRejectedBeforeDatabaseWork() {
    var slots = mock(SlotRepository.class);
    var bookings = mock(BookingRepository.class);
    Instant now = Instant.parse("2026-10-12T10:00:00Z");
    var service =
        new SchedulingService(
            slots, bookings, Clock.fixed(now, ZoneOffset.UTC), mock(AccountRepository.class));
    assertThatThrownBy(() -> service.publish(UUID.randomUUID(), now))
        .isInstanceOf(ApiException.class);
    verifyNoInteractions(slots, bookings);
  }

  @Test
  void withdrawnSlotCannotProduceABooking() {
    var slots = mock(SlotRepository.class);
    var bookings = mock(BookingRepository.class);
    Instant now = Instant.parse("2026-10-12T10:00:00Z");
    var slot = new Slot(UUID.randomUUID(), now.plusSeconds(3600));
    slot.withdrawn = true;
    when(slots.lock(slot.id)).thenReturn(Optional.of(slot));
    var service =
        new SchedulingService(
            slots, bookings, Clock.fixed(now, ZoneOffset.UTC), mock(AccountRepository.class));
    assertThatThrownBy(() -> service.book(UUID.randomUUID(), slot.id))
        .isInstanceOf(ApiException.class);
    verifyNoInteractions(bookings);
  }
}
