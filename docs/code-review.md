# Release review

Reviewed security, concurrency, correctness, database access, error responses, and operational behavior for the initial portfolio release.

| Finding | Resolution | Evidence |
| --- | --- | --- |
| Simultaneous bookings can race an availability check | Lock the slot in a transaction and enforce a partial unique index | Eight real HTTP requests produce one 201 and seven 409s; direct duplicate JDBC insert is rejected |
| Concurrent exclusion-index inserts can deadlock | Serialize publication on the staff account row | Overlapping-publication PostgreSQL test |
| Cancellation/withdrawal can invalidate a booking | Use the same slot lock for all three transitions | Withdrawal/booking race and cancellation/rebooking tests |
| Timestamp ties destabilize offset paging | Add UUID tie breakers and matching indexes | 51 tied records span two pages without duplicates or omissions for an unchanged dataset |
| Linux nanosecond clocks differ from PostgreSQL timestamp precision | Normalize persisted booking/expiry timestamps to microseconds | Regression clock deliberately supplies 123456789 nanoseconds; repeated cancellation stays identical |
| Extreme page numbers can exceed JPA's integer offset | Validate the supported offset range before querying | HTTP requests with Integer.MAX_VALUE pages return 400 |
| Authentication attempts were unlimited | PostgreSQL atomic per-address fixed-window counter | 40 concurrent increments allow exactly 20; HTTP overflow returns 429 |
| Authentication or SQL failures can expose confusing errors | Sanitize database failures as 503; keep lock conflicts at 409 | Filter failure tests and real conflict responses |
| Expired authentication data can grow indefinitely | Scheduled bounded cleanup | 1,001 expired sessions removed in two batches, live sessions/history retained |
| Operational details can leak through management endpoints | Public health hides details; metrics requires staff | Readiness/metrics access tests |

Passwords use BCrypt; opaque tokens are generated from 256 random bits and only their hashes are stored. Registration cannot assign a staff role. Staff bootstrap refuses to promote a customer. Ownership comes from the authenticated principal, and clients cannot submit another account's identity to booking or scheduling endpoints.

The release has 26 passing automated tests. Docker packaging and the disposable demonstration are recorded separately in verification notes. No known unresolved issue prevents the agreed portfolio workflow. This review is not an external penetration test or a proof of production readiness.

Deliberate limits: fixed-window rate limits permit a boundary burst and do not replace ingress-level abuse protection; forwarded headers require a trusted proxy configuration; offset pages can shift when the dataset changes; rescheduling is cancel-plus-book rather than atomic; booking retries do not use an idempotency key; email verification/recovery and public hosting remain outside the current release.
