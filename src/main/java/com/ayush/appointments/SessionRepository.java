package com.ayush.appointments;

import org.springframework.data.jpa.repository.JpaRepository;

interface SessionRepository extends JpaRepository<Session, String> {}
