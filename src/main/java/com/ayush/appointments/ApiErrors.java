package com.ayush.appointments;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
class ApiErrors {
  @ExceptionHandler(ApiException.class)
  ProblemDetail domain(ApiException e) {
    return ProblemDetail.forStatusAndDetail(e.status, e.getMessage());
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ProblemDetail constraint() {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.CONFLICT, "Conflicts with an existing account, slot, or booking");
  }

  @ExceptionHandler(org.springframework.dao.PessimisticLockingFailureException.class)
  ProblemDetail locking() {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.CONFLICT, "Concurrent schedule change; retry the request");
  }

  @ExceptionHandler(org.springframework.dao.DataAccessException.class)
  ProblemDetail database() {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.SERVICE_UNAVAILABLE, "Database is temporarily unavailable");
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail validation() {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request fields");
  }
}
