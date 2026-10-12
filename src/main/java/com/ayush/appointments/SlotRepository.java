package com.ayush.appointments;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

interface SlotRepository extends JpaRepository<Slot, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from Slot s where s.id=:id")
  Optional<Slot> lock(UUID id);

  @Query(
      "select s from Slot s where s.withdrawn=false and s.startsAt>=:from and s.startsAt<:to and"
          + " (:staff is null or s.staffId=:staff) and not exists (select b.id from Booking b where"
          + " b.slotId=s.id and b.cancelledAt is null) order by s.startsAt,s.id")
  List<Slot> available(Instant from, Instant to, UUID staff, Pageable page);

  List<Slot> findByStaffIdOrderByStartsAtAscIdAsc(UUID staffId, Pageable page);
}
