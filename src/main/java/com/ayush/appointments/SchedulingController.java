package com.ayush.appointments;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
class SchedulingController {
  private final SchedulingService service;

  SchedulingController(SchedulingService service) {
    this.service = service;
  }

  record Publish(@NotNull Instant startsAt) {}

  record Book(@NotNull UUID slotId) {}

  private UUID caller(Authentication authentication) {
    return (UUID) authentication.getPrincipal();
  }

  @PostMapping("/staff/slots")
  @ResponseStatus(HttpStatus.CREATED)
  Views.SlotView publish(Authentication a, @Valid @RequestBody Publish body) {
    return service.publish(caller(a), body.startsAt());
  }

  @GetMapping("/staff/slots")
  List<Views.SlotView> schedule(Authentication a, @RequestParam(defaultValue = "0") int page) {
    return service.schedule(caller(a), page);
  }

  @DeleteMapping("/staff/slots/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void withdraw(Authentication a, @PathVariable UUID id) {
    service.withdraw(caller(a), id);
  }

  @GetMapping("/slots")
  List<Views.SlotView> available(
      @RequestParam Instant from,
      @RequestParam Instant to,
      @RequestParam(required = false) UUID staffId,
      @RequestParam(defaultValue = "0") int page) {
    return service.available(from, to, staffId, page);
  }

  @PostMapping("/bookings")
  @ResponseStatus(HttpStatus.CREATED)
  Views.BookingView book(Authentication a, @Valid @RequestBody Book body) {
    return service.book(caller(a), body.slotId());
  }

  @GetMapping("/bookings")
  List<Views.BookingView> mine(Authentication a, @RequestParam(defaultValue = "0") int page) {
    return service.mine(caller(a), page);
  }

  @PostMapping("/bookings/{id}/cancel")
  Views.BookingView cancel(Authentication a, @PathVariable UUID id) {
    return service.cancel(caller(a), id);
  }
}
