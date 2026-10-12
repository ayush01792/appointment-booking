package com.ayush.appointments;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpStatus;

final class HttpProblems {
  private HttpProblems() {}

  static void write(
      ObjectMapper mapper, HttpServletResponse response, HttpStatus status, String detail)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType("application/problem+json");
    mapper.writeValue(
        response.getOutputStream(),
        Map.of(
            "type",
            "about:blank",
            "title",
            status.getReasonPhrase(),
            "status",
            status.value(),
            "detail",
            detail));
  }
}
