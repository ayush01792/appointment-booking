package com.ayush.appointments;

import java.time.*;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SchedulingService {
  private final SlotRepository slots;
  private final BookingRepository bookings;
  private final Clock clock;
  private final AccountRepository accounts;

  SchedulingService(
      SlotRepository slots, BookingRepository bookings, Clock clock, AccountRepository accounts) {
    this.slots = slots;
    this.bookings = bookings;
    this.clock = clock;
    this.accounts = accounts;
  }

  @Transactional
  Views.SlotView publish(UUID staff, Instant start) {
    if (!start.isAfter(clock.instant())) throw ApiException.bad("Slot must start in the future");
    if (start.getNano() != 0) throw ApiException.bad("Slot start must use whole seconds");
    // Serialize publication per staff before PostgreSQL checks exclusion constraints.
    accounts
        .lock(staff)
        .filter(a -> a.role == Account.Role.STAFF)
        .orElseThrow(ApiException::missing);
    return Views.SlotView.of(slots.saveAndFlush(new Slot(staff, start)));
  }

  @Transactional(readOnly = true)
  List<Views.SlotView> available(Instant from, Instant to, UUID staff, int page) {
    if ((page < 0 || page > Integer.MAX_VALUE / 50)
        || !to.isAfter(from)
        || Duration.between(from, to).compareTo(Duration.ofDays(31)) > 0)
      throw ApiException.bad("Use a positive window of at most 31 days and non-negative page");
    return slots
        .available(
            from.isBefore(clock.instant()) ? clock.instant() : from,
            to,
            staff,
            PageRequest.of(page, 50))
        .stream()
        .map(Views.SlotView::of)
        .toList();
  }

  @Transactional(readOnly = true)
  List<Views.SlotView> schedule(UUID staff, int page) {
    if ((page < 0 || page > Integer.MAX_VALUE / 50))
      throw ApiException.bad("Page is outside the supported range");
    return slots.findByStaffIdOrderByStartsAtAscIdAsc(staff, PageRequest.of(page, 50)).stream()
        .map(Views.SlotView::of)
        .toList();
  }

  @Transactional
  Views.BookingView book(UUID customer, UUID slotId) {
    Slot slot = slots.lock(slotId).orElseThrow(ApiException::missing);
    if (slot.withdrawn || !slot.startsAt.isAfter(clock.instant()))
      throw ApiException.conflict("Slot is unavailable");
    if (bookings.existsBySlotIdAndCancelledAtIsNull(slotId))
      throw ApiException.conflict("Slot is already booked");
    return Views.BookingView.of(
        bookings.saveAndFlush(new Booking(slotId, customer, clock.instant())), slot);
  }

  @Transactional(readOnly = true)
  List<Views.BookingView> mine(UUID customer, int page) {
    if ((page < 0 || page > Integer.MAX_VALUE / 50))
      throw ApiException.bad("Page is outside the supported range");
    var history =
        bookings.findByCustomerIdOrderByCreatedAtDescIdDesc(customer, PageRequest.of(page, 50));
    var byId = new HashMap<UUID, Slot>();
    slots
        .findAllById(history.stream().map(b -> b.slotId).distinct().toList())
        .forEach(s -> byId.put(s.id, s));
    return history.stream().map(b -> Views.BookingView.of(b, byId.get(b.slotId))).toList();
  }

  @Transactional
  Views.BookingView cancel(UUID customer, UUID bookingId) {
    UUID slotId = bookings.ownedSlot(bookingId, customer).orElseThrow(ApiException::missing);
    Slot slot = slots.lock(slotId).orElseThrow(ApiException::missing);
    // Load booking state only after acquiring the shared slot lock.
    Booking booking = bookings.findById(bookingId).orElseThrow(ApiException::missing);
    if (booking.cancelledAt == null)
      booking.cancelledAt = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    return Views.BookingView.of(bookings.saveAndFlush(booking), slot);
  }

  @Transactional
  void withdraw(UUID staff, UUID id) {
    Slot slot =
        slots.lock(id).filter(s -> s.staffId.equals(staff)).orElseThrow(ApiException::missing);
    if (bookings.existsBySlotIdAndCancelledAtIsNull(id))
      throw ApiException.conflict(
          "The customer must cancel the active booking before this slot can be withdrawn");
    slot.withdrawn = true;
  }
}
