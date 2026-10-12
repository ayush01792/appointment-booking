# Public appointment-booking demo

- API explorer: https://appointment-booking-17zj.onrender.com/swagger-ui/index.html
- OpenAPI: https://appointment-booking-17zj.onrender.com/v3/api-docs
- Readiness: https://appointment-booking-17zj.onrender.com/actuator/health/readiness
- Repository: https://github.com/ayush01792/appointment-booking

This is a backend portfolio demo. Use Swagger UI to register a customer, log in, and supply the returned bearer token with **Authorize**. Follow the repository's API walkthrough. Staff sign-in uses `staff@example.test`; its generated password is available only in your Render service's **Environment → BOOTSTRAP_STAFF_PASSWORD**. Keep that password private.

The API and PostgreSQL use Render's free plans in Singapore. The database accepts internal connections only. The application binds to Render's assigned port, applies Flyway migrations, and uses database readiness for health checks. Future application deployments wait for GitHub checks to pass. The Blueprint is committed as `render.yaml`.

The database expires on **11 November 2026**, at 18:25:54 UTC (23:55:54 IST), unless you upgrade it. Free API instances sleep after inactivity, so an initial request can take a minute or longer. Free hours are shared with your other services in this workspace. No paid resource was selected.

Use test accounts and sample appointments. The verification flow leaves customer accounts, cancelled booking history, and a withdrawn test slot in this demo database. Authentication limiting uses the direct upstream address; behind Render's proxy, users may share a rate-limit bucket. This deployment is suitable for a portfolio demonstration, not production customer operation.

To stop the demo, suspend its API in Render. Manage or export the database before its free expiry. Do not modify your other Render services.
