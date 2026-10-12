package com.ayush.appointments;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface AccountRepository extends JpaRepository<Account, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from Account a where a.id=:id")
  Optional<Account> lock(UUID id);

  Optional<Account> findByEmail(String email);
}
