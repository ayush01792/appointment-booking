# Verified initial release — 12 October 2026

## Release checks

- **24 automated tests passed:** 20 real HTTP/PostgreSQL integration tests and 4 unit tests. Zero failures, errors, or skips in a clean Maven 3.9.16 verification run on Java 21.
- PostgreSQL **17.11** applied all three Flyway migrations and Hibernate validated the resulting schema.
- Eight simultaneous booking requests produced exactly one 201 and seven 409 responses, with one active booking in the database. A direct duplicate JDBC insert was rejected.
- Overlapping staff publication and booking/withdrawal race checks passed. Cancellation preserved history and allowed rebooking. Ownership, roles, validation, hashed passwords/tokens, expiry/logout, and explicit staff bootstrap passed.
- Forty concurrent authentication-counter increments allowed exactly twenty. HTTP overflow returned 429 with Retry-After; spoofed forwarded addresses did not bypass the limit.
- Cleanup removed 1,001 expired sessions in two bounded batches while preserving live sessions and booking history.
- Stable pagination of 51 tied records, extreme page validation, readiness, staff-only metrics, and sanitized database failures passed.
- JaCoCo measured **96.9% line coverage** (284 covered lines, 9 missed) and **80.6% branch coverage** (58 covered branches, 14 missed). The report is generated under `target/site/jacoco/`.
- GitHub CI built the final source Docker image successfully; the image runs as user `app`.
- The real Docker app and PostgreSQL passed **19 end-to-end demo checks**, including the eight-request race. [Recorded JSON](demo-result.json) and [generated OpenAPI snapshot](openapi.json) are included. Demo credentials were generated at runtime and were neither printed nor recorded.
- The disposable demo containers and database volume were removed by the script. The PayFlow Kind container was temporarily paused with user permission for this local demo and resumed afterward; it was not deleted or reconfigured.

A Linux CI regression exposed nanosecond timestamps changing after PostgreSQL persisted them with microsecond precision. Creation, cancellation, and session expiry now use microsecond precision; the test clock deliberately includes nanoseconds to exercise this behavior across platforms.

## Reproduce

Requirements: Java 21, Docker, and Python 3 for the demo.

```sh
./mvnw -B clean verify
./scripts/demo.sh
```

On Docker Desktop, if socket discovery needs explicit configuration:

```sh
export DOCKER_HOST="unix://$HOME/.docker/run/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
./mvnw -B clean verify
```

The test JVM is capped at 384 MB. Local Compose limits the app and database to 512 MB and 256 MB; PostgreSQL uses 32 MB shared buffers and a 20-connection limit. The demo checks TCP database readiness, waits up to five minutes for app readiness, and permits configurable client timeouts. Its first attempts exposed overly short demo timeouts under a busy local Docker environment; the corrected demo completed successfully. No cold-start or request-latency benchmark is claimed.

## CI and scope

GitHub Actions verifies tests, builds the Docker image, exercises the disposable demo, and retains reports for 14 days. [Run 38291578214](https://github.com/ayush01792/appointment-booking/actions/runs/38291578214) passed all steps for source commit `e06fbea292f69d8ab96f60c1b0d66c4bf2285dad`: 24 tests, the Docker build, and 19 demo checks. The JSON and OpenAPI snapshots included here come from that run. The downloaded reports archive matched GitHub’s SHA-256 digest. Subsequent documentation updates do not change the tested application source.

These checks establish correctness for the exercised scenarios, not a formal proof of every possible interleaving, an external security audit, or a production performance claim. The free Render portfolio demo was deployed and passed 19 checks over public HTTPS, including the eight-request booking race. [Hosted demo results](hosted-demo-result.json) and [hosting details](hosting-guide.md) are included. Production customer operation was not performed.

The hosted API uses a relative OpenAPI server URL (`/`) so Swagger requests remain on HTTPS behind Render’s proxy. The HTTP integration test asserts this configuration.
