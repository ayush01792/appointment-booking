package com.ayush.appointments;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
class AuthController {
  private final AuthService auth;

  AuthController(AuthService auth) {
    this.auth = auth;
  }

  record Credentials(
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(min = 12, max = 72) String password) {}

  @io.swagger.v3.oas.annotations.security.SecurityRequirements
  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  Views.AccountView register(@Valid @RequestBody Credentials body) {
    return auth.register(body.email(), body.password());
  }

  @io.swagger.v3.oas.annotations.security.SecurityRequirements
  @PostMapping("/login")
  AuthService.Token login(@Valid @RequestBody Credentials body) {
    return auth.login(body.email(), body.password());
  }

  @GetMapping("/me")
  Views.AccountView me(org.springframework.security.core.Authentication authentication) {
    return auth.current((java.util.UUID) authentication.getPrincipal());
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void logout(@RequestHeader("Authorization") String header) {
    auth.logout(header.substring(7));
  }
}
