# Interactive frontend verification — 13 October 2026

Source commit: `bef5d3166c395ec5d0c74c616b0413f2a6447650`.
[Passing GitHub CI](https://github.com/ayush01792/appointment-booking/actions/runs/38295531915).

The full suite passed **26 tests**, with zero failures, errors, or skips. CI built the Docker image and passed **19 end-to-end API checks**, including the eight-request booking race. CI JaCoCo measured **97.0% line coverage** (293 covered, 9 missed) and **80.6% branch coverage** (58 covered, 14 missed). These numbers concern Java code, not JavaScript coverage.

Browser verification used the real Spring Boot app with a disposable PostgreSQL database:

- Staff sign-in selected the staff navigation using server-provided account identity.
- Staff published a future 30-minute opening through the date/time dialog.
- A customer registered, signed in, selected the day, and confirmed the real booking.
- Customer history displayed the correct date, start time, end time, and upcoming status.
- Cancellation released the slot and preserved readable cancelled history.
- Reload restored the tab's session and validated it with the server.
- Sign-out returned the guest interface and revoked the bearer token.
- Staff withdrew the cancelled opening from the schedule.
- Desktop layout was inspected at 1280 pixels. At a 390-pixel mobile width, document width and scroll width were both 390 pixels, with no horizontal overflow.

The frontend is served from the same origin as the API. External JavaScript is disallowed by its Content Security Policy; no external scripts, fonts, or UI packages are required. Dialogs use native focus handling, controls have accessible names, error messages use alerts, and reduced-motion preferences are respected.

Browser verification is a manual walkthrough, not a comprehensive browser automation suite or an accessibility certification. The free Render database expiry remains 11 November 2026.

The final Render deployment is live at https://appointment-booking-17zj.onrender.com/. The public root returned HTTP 200 and loaded the working frontend. Staff sign-in and publishing, customer registration, availability browsing, booking, and cancellation were also verified in the deployed browser, with no console errors. Three future sample openings were published to make the demo immediately usable.
