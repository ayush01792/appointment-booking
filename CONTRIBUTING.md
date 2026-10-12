# Contributing

Use Java 21 and the Maven wrapper. Run `./mvnw -B clean verify` with Docker available before submitting a change. Integration tests require PostgreSQL through Testcontainers and intentionally fail if Docker is unavailable.

Keep changes focused. Describe the behavior being fixed, the design tradeoff, and relevant test evidence. Concurrency changes should include a real PostgreSQL test; an H2 substitute does not exercise the production constraints.

Add a new Flyway migration for schema changes; do not edit migrations already applied to a shared database. Keep real credentials, `.env`, generated binaries, and personal data out of commits. API changes should update the README and the generated OpenAPI snapshot through the demo.
