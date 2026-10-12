package com.ayush.appointments;

import org.springframework.http.HttpStatus;

class ApiException extends RuntimeException {
  final HttpStatus status;

  ApiException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }

  static ApiException missing() {
    return new ApiException(HttpStatus.NOT_FOUND, "Resource not found");
  }

  static ApiException conflict(String message) {
    return new ApiException(HttpStatus.CONFLICT, message);
  }

  static ApiException bad(String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, message);
  }
}
