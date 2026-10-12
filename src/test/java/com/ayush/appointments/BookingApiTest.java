package com.ayush.appointments;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@org.springframework.context.annotation.Import(BookingApiTest.TimeConfiguration.class)
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BookingApiTest {
  @org.springframework.boot.test.context.TestConfiguration
  static class TimeConfiguration {
    @org.springframework.context.annotation.Bean
    @org.springframework.context.annotation.Primary
    Clock testClock() {
      return Clock.fixed(
          Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).plusNanos(123456789),
          ZoneOffset.UTC);
    }
  }

  @Autowired Clock clock;
  @Autowired AuthRateLimiter limiter;
  @Autowired SessionMaintenance maintenance;

  @Container
  static final PostgreSQLContainer<?> DB =
      new PostgreSQLContainer<>("postgres:17.11")
          .withDatabaseName("appointments")
          .withUsername("test")
          .withPassword("test-only-password")
          .withCreateContainerCmdModifier(c -> c.getHostConfig().withMemory(256L * 1024 * 1024));

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", DB::getJdbcUrl);
    r.add("spring.datasource.username", DB::getUsername);
    r.add("spring.datasource.password", DB::getPassword);
  }

  @Autowired TestRestTemplate http;
  @Autowired AccountRepository accounts;
  @Autowired PasswordEncoder encoder;
  @Autowired JdbcTemplate jdbc;
  String staff, customer, otherStaff, otherCustomer;
  Instant start;
  static final String PASSWORD = "portfolio-test-password";

  @BeforeEach
  void setup() {
    jdbc.execute("TRUNCATE auth_rate_limits, sessions, bookings, slots, accounts CASCADE");
    staff = create("staff@example.test", Account.Role.STAFF);
    customer = create("customer@example.test", Account.Role.CUSTOMER);
    otherStaff = create("staff2@example.test", Account.Role.STAFF);
    otherCustomer = create("customer2@example.test", Account.Role.CUSTOMER);
    start = Instant.now().plusSeconds(86400).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
  }

  String create(String email, Account.Role role) {
    accounts.saveAndFlush(new Account(email, encoder.encode(PASSWORD), role));
    var login =
        call(
            HttpMethod.POST, "/api/auth/login", null, Map.of("email", email, "password", PASSWORD));
    assertThat(Instant.parse(login.getBody().get("expiresAt").asText()).getNano() % 1000).isZero();
    return login.getBody().get("accessToken").asText();
  }

  ResponseEntity<JsonNode> call(HttpMethod method, String path, String token, Object body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    if (token != null) headers.setBearerAuth(token);
    return http.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
  }

  UUID publish() {
    var result =
        call(HttpMethod.POST, "/api/staff/slots", staff, Map.of("startsAt", start.toString()));
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return UUID.fromString(result.getBody().get("id").asText());
  }

  ResponseEntity<JsonNode> book(String token, UUID slot) {
    return call(HttpMethod.POST, "/api/bookings", token, Map.of("slotId", slot));
  }

  @Test
  void completeWorkflowCancellationAndRebooking() {
    UUID slot = publish();
    String search = "/api/slots?from=" + start.minusSeconds(1) + "&to=" + start.plusSeconds(3600);
    assertThat(call(HttpMethod.GET, search, customer, null).getBody().size()).isEqualTo(1);
    var booked = book(customer, slot);
    assertThat(booked.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(Instant.parse(booked.getBody().get("createdAt").asText()).getNano() % 1000).isZero();
    assertThat(call(HttpMethod.GET, search, customer, null).getBody().size()).isZero();
    assertThat(call(HttpMethod.GET, "/api/bookings", customer, null).getBody().size()).isEqualTo(1);
    String id = booked.getBody().get("id").asText();
    var cancelled = call(HttpMethod.POST, "/api/bookings/" + id + "/cancel", customer, null);
    assertThat(cancelled.getBody().get("cancelledAt").isTextual()).isTrue();
    assertThat(
            call(HttpMethod.POST, "/api/bookings/" + id + "/cancel", customer, null)
                .getBody()
                .get("cancelledAt"))
        .isEqualTo(cancelled.getBody().get("cancelledAt"));
    assertThat(book(otherCustomer, slot).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM bookings WHERE cancelled_at IS NULL", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void simultaneousRequestsHaveExactlyOneWinner() throws Exception {
    UUID slot = publish();
    int clients = 8;
    var barrier = new CyclicBarrier(clients);
    try (var pool = Executors.newFixedThreadPool(clients)) {
      List<Future<Integer>> responses = new ArrayList<>();
      for (int i = 0; i < clients; i++) {
        final String token = (i % 2 == 0) ? customer : otherCustomer;
        responses.add(
            pool.submit(
                () -> {
                  barrier.await(10, TimeUnit.SECONDS);
                  return book(token, slot).getStatusCode().value();
                }));
      }
      List<Integer> statuses = new ArrayList<>();
      for (var response : responses) statuses.add(response.get(30, TimeUnit.SECONDS));
      assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
      assertThat(statuses).filteredOn(s -> s == 409).hasSize(7);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM bookings WHERE cancelled_at IS NULL", Integer.class))
          .isEqualTo(1);
    }
  }

  @Test
  void uniqueIndexRejectsBookingThatBypassesService() {
    UUID slot = publish();
    book(customer, slot);
    UUID account = accounts.findByEmail("customer2@example.test").orElseThrow().id;
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO bookings(id,slot_id,customer_id,created_at) VALUES (?,?,?,now())",
                    UUID.randomUUID(),
                    slot,
                    account))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  void customerCannotCancelAnotherCustomersBooking() {
    String id = book(customer, publish()).getBody().get("id").asText();
    assertThat(
            call(HttpMethod.POST, "/api/bookings/" + id + "/cancel", otherCustomer, null)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(call(HttpMethod.GET, "/api/bookings", otherCustomer, null).getBody().size())
        .isZero();
  }

  @Test
  void roleAndStaffOwnershipAreEnforced() {
    assertThat(
            call(HttpMethod.POST, "/api/staff/slots", customer, Map.of("startsAt", start))
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    UUID slot = publish();
    assertThat(book(staff, slot).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            call(HttpMethod.DELETE, "/api/staff/slots/" + slot, otherStaff, null).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(call(HttpMethod.GET, "/api/staff/slots", otherStaff, null).getBody().size())
        .isZero();
  }

  @Test
  void overlappingSlotsConflictButAdjacentSlotsWork() {
    publish();
    assertThat(
            call(
                    HttpMethod.POST,
                    "/api/staff/slots",
                    staff,
                    Map.of("startsAt", start.plusSeconds(900)))
                .getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(
            call(
                    HttpMethod.POST,
                    "/api/staff/slots",
                    staff,
                    Map.of("startsAt", start.plusSeconds(1800)))
                .getStatusCode())
        .isEqualTo(HttpStatus.CREATED);
    assertThat(
            call(HttpMethod.POST, "/api/staff/slots", otherStaff, Map.of("startsAt", start))
                .getStatusCode())
        .isEqualTo(HttpStatus.CREATED);
  }

  @Test
  void simultaneousOverlappingPublicationHasOneWinner() throws Exception {
    var barrier = new CyclicBarrier(2);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a =
          pool.submit(
              () -> {
                barrier.await(10, TimeUnit.SECONDS);
                return call(HttpMethod.POST, "/api/staff/slots", staff, Map.of("startsAt", start))
                    .getStatusCode()
                    .value();
              });
      var b =
          pool.submit(
              () -> {
                barrier.await(10, TimeUnit.SECONDS);
                return call(
                        HttpMethod.POST,
                        "/api/staff/slots",
                        staff,
                        Map.of("startsAt", start.plusSeconds(900)))
                    .getStatusCode()
                    .value();
              });
      assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(201, 409);
    }
  }

  @Test
  void withdrawalCannotRemoveAnActiveBooking() {
    UUID slot = publish();
    String id = book(customer, slot).getBody().get("id").asText();
    assertThat(call(HttpMethod.DELETE, "/api/staff/slots/" + slot, staff, null).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    call(HttpMethod.POST, "/api/bookings/" + id + "/cancel", customer, null);
    assertThat(call(HttpMethod.DELETE, "/api/staff/slots/" + slot, staff, null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(book(customer, slot).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(
            call(HttpMethod.POST, "/api/staff/slots", staff, Map.of("startsAt", start))
                .getStatusCode())
        .isEqualTo(HttpStatus.CREATED);
  }

  @Test
  void withdrawalRacingBookingPreservesLegalState() throws Exception {
    UUID slot = publish();
    var barrier = new CyclicBarrier(2);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var booking =
          pool.submit(
              () -> {
                barrier.await(10, TimeUnit.SECONDS);
                return book(customer, slot).getStatusCode().value();
              });
      var withdrawal =
          pool.submit(
              () -> {
                barrier.await(10, TimeUnit.SECONDS);
                return call(HttpMethod.DELETE, "/api/staff/slots/" + slot, staff, null)
                    .getStatusCode()
                    .value();
              });
      int b = booking.get(30, TimeUnit.SECONDS), w = withdrawal.get(30, TimeUnit.SECONDS);
      assertThat((b == 201 && w == 409) || (b == 409 && w == 204)).isTrue();
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM bookings b JOIN slots s ON s.id=b.slot_id WHERE"
                      + " b.cancelled_at IS NULL AND s.withdrawn",
                  Integer.class))
          .isZero();
    }
  }

  @Test
  void validationRejectsPastSlotsMalformedRequestsAndUnboundedSearch() {
    assertThat(
            call(
                    HttpMethod.POST,
                    "/api/staff/slots",
                    staff,
                    Map.of("startsAt", Instant.now().minusSeconds(60)))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(call(HttpMethod.POST, "/api/bookings", customer, Map.of()).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            call(
                    HttpMethod.GET,
                    "/api/slots?from=" + start + "&to=" + start.plusSeconds(32L * 86400),
                    customer,
                    null)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    UUID slot = publish();
    jdbc.update(
        "UPDATE slots SET starts_at=starts_at-interval '2 days',ends_at=ends_at-interval '2 days'"
            + " WHERE id=?",
        slot);
    assertThat(book(customer, slot).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void registrationCannotEscalateRoleAndNormalizesEmail() {
    var registration =
        call(
            HttpMethod.POST,
            "/api/auth/register",
            null,
            Map.of("email", "New@Example.test", "password", PASSWORD));
    assertThat(registration.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(registration.getBody().get("role").asText()).isEqualTo("CUSTOMER");
    assertThat(registration.getBody().get("email").asText()).isEqualTo("new@example.test");
    assertThat(
            call(
                    HttpMethod.POST,
                    "/api/auth/register",
                    null,
                    Map.of("email", "new@example.test", "password", PASSWORD))
                .getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(
            call(
                    HttpMethod.POST,
                    "/api/auth/register",
                    null,
                    Map.of("email", "evil@example.test", "password", PASSWORD, "role", "STAFF"))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(accounts.findByEmail("new@example.test").orElseThrow().passwordHash)
        .isNotEqualTo(PASSWORD);
  }

  @Test
  void tokensExpireAreHashedAndCanBeRevoked() {
    assertThat(call(HttpMethod.GET, "/api/bookings", null, null).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(call(HttpMethod.GET, "/api/bookings", "invalid", null).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM sessions WHERE token_hash=?", Integer.class, customer))
        .isZero();
    assertThat(call(HttpMethod.POST, "/api/auth/logout", customer, null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(call(HttpMethod.GET, "/api/bookings", customer, null).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    jdbc.update(
        "UPDATE sessions SET expires_at=?",
        java.sql.Timestamp.from(clock.instant().minusSeconds(1)));
    assertThat(call(HttpMethod.GET, "/api/bookings", otherCustomer, null).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void badCredentialsAndOversizedUnicodePasswordsFailCleanly() {
    assertThat(
            call(
                    HttpMethod.POST,
                    "/api/auth/login",
                    null,
                    Map.of("email", "customer@example.test", "password", "wrong-password-value"))
                .getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(
            call(
                    HttpMethod.POST,
                    "/api/auth/register",
                    null,
                    Map.of("email", "unicode@example.test", "password", "界".repeat(30)))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void openApiIsAvailableAndDocumentsBookingOperations() {
    var docs = call(HttpMethod.GET, "/v3/api-docs", null, null);
    assertThat(docs.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(docs.getBody().get("paths").has("/api/bookings")).isTrue();
  }

  @Test
  void explicitBootstrapCreatesStaffWithoutPromotingCustomers() {
    var environment =
        new org.springframework.mock.env.MockEnvironment()
            .withProperty("bootstrap.staff.email", "newstaff@example.test")
            .withProperty("bootstrap.staff.password", PASSWORD);
    var bootstrap = new StaffBootstrap(accounts, encoder, environment);
    bootstrap.run(new org.springframework.boot.DefaultApplicationArguments());
    var created = accounts.findByEmail("newstaff@example.test").orElseThrow();
    assertThat(created.role).isEqualTo(Account.Role.STAFF);
    assertThat(encoder.matches(PASSWORD, created.passwordHash)).isTrue();
    String originalHash = created.passwordHash;
    bootstrap.run(new org.springframework.boot.DefaultApplicationArguments());
    assertThat(accounts.findByEmail("newstaff@example.test").orElseThrow().passwordHash)
        .isEqualTo(originalHash);
    environment.withProperty("bootstrap.staff.email", "customer@example.test");
    assertThatThrownBy(
            () -> bootstrap.run(new org.springframework.boot.DefaultApplicationArguments()))
        .isInstanceOf(IllegalStateException.class);
    assertThat(accounts.findByEmail("customer@example.test").orElseThrow().role)
        .isEqualTo(Account.Role.CUSTOMER);
  }

  @Test
  void authRateLimitReturns429AndIgnoresSpoofedForwardedAddresses() {
    jdbc.execute("TRUNCATE auth_rate_limits");
    for (int i = 0; i < 20; i++) {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.set("X-Forwarded-For", "203.0.113." + i);
      var response =
          http.postForEntity(
              "/api/auth/login",
              new HttpEntity<>(
                  Map.of("email", "missing@example.test", "password", PASSWORD), headers),
              JsonNode.class);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
    var blocked =
        call(
            HttpMethod.POST,
            "/api/auth/login",
            null,
            Map.of("email", "missing@example.test", "password", PASSWORD));
    assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(Long.parseLong(blocked.getHeaders().getFirst("Retry-After"))).isBetween(1L, 60L);
    assertThat(blocked.getBody().get("title").asText()).isEqualTo("Too Many Requests");
    assertThat(call(HttpMethod.GET, "/api/bookings", customer, null).getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  void parallelAuthAttemptsCannotBypassTheDatabaseCounter() throws Exception {
    try (var pool = Executors.newFixedThreadPool(8)) {
      List<Future<Boolean>> results = new ArrayList<>();
      for (int i = 0; i < 40; i++)
        results.add(pool.submit(() -> limiter.record("192.0.2.123").allowed()));
      int allowed = 0;
      for (var result : results) if (result.get(30, TimeUnit.SECONDS)) allowed++;
      assertThat(allowed).isEqualTo(20);
      assertThat(
              jdbc.queryForObject(
                  "SELECT requests FROM auth_rate_limits WHERE client_hash=?",
                  Long.class,
                  AuthService.hash("192.0.2.123")))
          .isEqualTo(40L);
    }
  }

  @Test
  void maintenancePurgesInBoundedBatchesAndPreservesLiveSessionsAndHistory() {
    UUID owner = accounts.findByEmail("customer@example.test").orElseThrow().id;
    book(customer, publish());
    jdbc.update(
        "INSERT INTO sessions(token_hash,account_id,expires_at) SELECT lpad(i::text,64,'0'),?,?"
            + " FROM generate_series(1,1001) i",
        owner,
        java.sql.Timestamp.from(clock.instant().minusSeconds(1)));
    jdbc.update(
        "INSERT INTO auth_rate_limits(client_hash,window_start,requests) VALUES (?,?,1)",
        AuthService.hash("old-window"),
        clock.instant().getEpochSecond() - 120);
    var purged = maintenance.purgeExpired();
    assertThat(purged.sessions()).isEqualTo(1000);
    assertThat(purged.rateWindows()).isEqualTo(1);
    assertThat(maintenance.purgeExpired().sessions()).isEqualTo(1);
    assertThat(call(HttpMethod.GET, "/api/bookings", customer, null).getBody().size()).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM sessions", Integer.class)).isEqualTo(4);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_rate_limits", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void readinessIsPublicAndMetricsAreStaffOnly() {
    var health = call(HttpMethod.GET, "/actuator/health/readiness", null, null);
    assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(health.getBody().get("status").asText()).isEqualTo("UP");
    assertThat(health.getBody().has("components")).isFalse();
    assertThat(call(HttpMethod.GET, "/actuator/metrics", null, null).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(call(HttpMethod.GET, "/actuator/metrics", customer, null).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(call(HttpMethod.GET, "/actuator/metrics", staff, null).getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  void tiedTimestampsHaveStablePaginationAndOversizedPagesAreRejected() {
    UUID staffId = accounts.findByEmail("staff@example.test").orElseThrow().id;
    UUID customerId = accounts.findByEmail("customer@example.test").orElseThrow().id;
    jdbc.update(
        "INSERT INTO slots(id,staff_id,starts_at,ends_at,withdrawn) SELECT"
            + " md5(i::text)::uuid,?,?,?,true FROM generate_series(1,51) i",
        staffId,
        java.sql.Timestamp.from(start),
        java.sql.Timestamp.from(start.plusSeconds(1800)));
    jdbc.update(
        "INSERT INTO bookings(id,slot_id,customer_id,created_at,cancelled_at) SELECT"
            + " md5('booking'||s.id::text)::uuid,s.id,?,?,? FROM slots s",
        customerId,
        java.sql.Timestamp.from(clock.instant()),
        java.sql.Timestamp.from(clock.instant()));
    for (String endpoint : List.of("/api/staff/slots", "/api/bookings")) {
      String token = endpoint.contains("staff") ? staff : customer;
      var first = call(HttpMethod.GET, endpoint + "?page=0", token, null).getBody();
      var second = call(HttpMethod.GET, endpoint + "?page=1", token, null).getBody();
      assertThat(first.size()).isEqualTo(50);
      assertThat(second.size()).isEqualTo(1);
      Set<String> ids = new HashSet<>(first.findValuesAsText("id"));
      ids.addAll(second.findValuesAsText("id"));
      assertThat(ids).hasSize(51);
      assertThat(call(HttpMethod.GET, endpoint + "?page=0", token, null).getBody())
          .isEqualTo(first);
      assertThat(
              call(HttpMethod.GET, endpoint + "?page=" + Integer.MAX_VALUE, token, null)
                  .getStatusCode())
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }
  }
}
