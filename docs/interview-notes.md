# Interview and resume notes

## Resume bullets supported by the project

- Built a Java 21/Spring Boot appointment-booking API with PostgreSQL transactions, staff/customer authorization, Flyway migrations, and OpenAPI documentation.
- Prevented duplicate active bookings with row locks and a partial unique index; verified that eight simultaneous HTTP requests produced one successful booking and seven conflicts in PostgreSQL tests.
- Added atomic authentication rate limiting, bounded expired-session cleanup, readiness checks, and a Docker demo; validated the backend with 26 automated tests.

Keep these as engineering/correctness claims. No production traffic, throughput, latency, revenue, or customer adoption was measured.

## STAR explanation

**Situation:** A booking system can show a slot as available to multiple customers at once. A simple read-then-insert sequence can create duplicate bookings.

**Task:** Build an original backend in which staff schedules and customer bookings remain valid under simultaneous requests.

**Action:** Model slots and booking history separately; acquire the slot row lock inside a transaction; enforce one active booking with a PostgreSQL partial unique index; use a GiST exclusion constraint for overlapping staff slots. Apply the same slot lock to cancellation and withdrawal. Take staff/customer identities from bearer authentication and check ownership. Test races through the real HTTP server and PostgreSQL, not an in-memory database.

**Result:** In the verified eight-request race, one booking succeeded and seven returned 409; the database contained exactly one active booking. A direct duplicate insert was also rejected. The final automated suite contains 26 passing tests, with zero skips in the recorded local release run.

## Questions to prepare

**Why both a row lock and a unique index?** The lock makes the service's check and update consistent across concurrent requests. The unique index remains the final guard if another code path bypasses the service.

**What happens when cancellation races a new booking?** Both operations lock the same slot. The next operation sees the committed state after obtaining the lock. A new booking succeeds only if the previous booking was already cancelled.

**Why lock the staff account when publishing?** Concurrent inserts into a PostgreSQL exclusion index initially caused a deadlock. Serializing publication per staff avoids that path while retaining the database exclusion constraint. Different staff can publish independently.

**Why opaque tokens?** Random one-hour tokens make revocation straightforward. Only their hashes are stored. Each request checks current account state, so roles are not frozen in a long-lived signed payload. The tradeoff is a database lookup per authenticated request.

**Why no Kafka/Redis/Kubernetes?** The current workflow is handled by one application and PostgreSQL transactions. More infrastructure would add operations work without solving a present requirement.

**How is pagination stable?** Sort timestamp ties by UUID and index that order. Offset pagination still moves when the underlying dataset changes; a cursor would be a useful later choice for very large histories.

**What is outside scope?** Email verification/recovery, public hosting, backup operations, and production load measurements. The demo is a disposable local workflow; the test numbers establish correctness, not performance.
