package com.ayush.appointments;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuthService {
  private final AccountRepository accounts;
  private final SessionRepository sessions;
  private final PasswordEncoder encoder;
  private final Clock clock;
  private final String dummyPasswordHash;
  private final SecureRandom random = new SecureRandom();

  AuthService(
      AccountRepository accounts,
      SessionRepository sessions,
      PasswordEncoder encoder,
      Clock clock) {
    this.accounts = accounts;
    this.sessions = sessions;
    this.encoder = encoder;
    this.clock = clock;
    this.dummyPasswordHash = encoder.encode(UUID.randomUUID().toString());
  }

  static String normalize(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  static String hash(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  @Transactional
  Views.AccountView register(String email, String password) {
    validatePassword(password);
    return Views.AccountView.of(
        accounts.saveAndFlush(
            new Account(normalize(email), encoder.encode(password), Account.Role.CUSTOMER)));
  }

  static void validatePassword(String password) {
    if (password.getBytes(StandardCharsets.UTF_8).length > 72)
      throw ApiException.bad("Password must be at most 72 UTF-8 bytes");
  }

  record Token(String accessToken, String tokenType, Instant expiresAt) {}

  @Transactional
  Token login(String email, String password) {
    validatePassword(password);
    Account account = accounts.findByEmail(normalize(email)).orElse(null);
    // Unknown accounts still pay the BCrypt verification cost.
    boolean matches =
        encoder.matches(password, account == null ? dummyPasswordHash : account.passwordHash);
    if (account == null || !matches)
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    Instant expiry =
        clock.instant().plusSeconds(3600).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    sessions.save(new Session(hash(token), account.id, expiry));
    return new Token(token, "Bearer", expiry);
  }

  @Transactional(readOnly = true)
  Optional<Account> authenticate(String token) {
    if (token.length() != 43) return Optional.empty();
    return sessions
        .findById(hash(token))
        .filter(s -> s.expiresAt.isAfter(clock.instant()))
        .flatMap(s -> accounts.findById(s.accountId));
  }

  @Transactional
  void logout(String token) {
    sessions.deleteById(hash(token));
  }
}
