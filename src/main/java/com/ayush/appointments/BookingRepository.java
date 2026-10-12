package com.ayush.appointments;

import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface BookingRepository extends JpaRepository<Booking, UUID> {
  @Query("select b.slotId from Booking b where b.id=:id and b.customerId=:customer")
  Optional<UUID> ownedSlot(UUID id, UUID customer);

  boolean existsBySlotIdAndCancelledAtIsNull(UUID slotId);

  List<Booking> findByCustomerIdOrderByCreatedAtDescIdDesc(UUID customerId, Pageable page);
}
