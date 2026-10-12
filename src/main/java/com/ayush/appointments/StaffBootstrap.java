package com.ayush.appointments;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "bootstrap.staff.enabled", havingValue = "true")
class StaffBootstrap implements ApplicationRunner {
  private final AccountRepository accounts;
  private final PasswordEncoder encoder;
  private final Environment env;

  StaffBootstrap(AccountRepository accounts, PasswordEncoder encoder, Environment env) {
    this.accounts = accounts;
    this.encoder = encoder;
    this.env = env;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    String email = AuthService.normalize(env.getRequiredProperty("bootstrap.staff.email"));
    String password = env.getRequiredProperty("bootstrap.staff.password");
    if (!email.matches("[^@\\s]+@[^@\\s]+[.][^@\\s]+")
        || email.length() > 254
        || password.length() < 12
        || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new IllegalArgumentException(
          "Valid staff email and password (at least 12 characters, at most 72 UTF-8 bytes)"
              + " required");
    var existing = accounts.findByEmail(email);
    if (existing.isPresent()) {
      if (existing.get().role != Account.Role.STAFF)
        throw new IllegalStateException("Existing email belongs to a customer");
      return;
    }
    accounts.saveAndFlush(new Account(email, encoder.encode(password), Account.Role.STAFF));
  }
}
