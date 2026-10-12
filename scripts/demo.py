#!/usr/bin/env python3
"""Exercise the real API without printing or saving authentication credentials."""
import argparse
import concurrent.futures
import datetime as dt
import json
import os
from pathlib import Path
import secrets
import threading
import time
import urllib.error
import urllib.parse
import urllib.request

parser = argparse.ArgumentParser()
parser.add_argument("--report", type=Path, help="Save the non-sensitive demo summary")
parser.add_argument("--openapi", type=Path, help="Save the generated OpenAPI specification")
args = parser.parse_args()
base = os.environ["DEMO_BASE_URL"]
checks = []

def request(method, path, payload=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    body = None if payload is None else json.dumps(payload).encode()
    req = urllib.request.Request(base + path, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=3 if path == "/actuator/health/readiness" else float(os.environ.get("DEMO_REQUEST_TIMEOUT_SECONDS", "60"))) as response:
            raw = response.read()
            return response.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as error:
        raw = error.read()
        return error.code, json.loads(raw) if raw else None

def check(label, condition):
    if not condition:
        raise RuntimeError("Demo check failed: " + label)
    checks.append(label)
    print("PASS " + label, flush=True)

deadline=time.monotonic()+float(os.environ.get("DEMO_READY_TIMEOUT_SECONDS", "300"))
print("Waiting for the application and PostgreSQL readiness...", flush=True)
while time.monotonic()<deadline:
    try:
        status, body = request("GET", "/actuator/health/readiness")
        if status == 200 and body.get("status") == "UP":
            break
    except (urllib.error.URLError, TimeoutError, ConnectionError):
        pass
    time.sleep(1)
else:
    raise RuntimeError("Application did not become ready")
check("Readiness includes PostgreSQL", True)
status, body = request("POST", "/api/auth/login", {"email": "staff@demo.example.test", "password": os.environ["DEMO_STAFF_PASSWORD"]})
check("Bootstrapped staff can log in", status == 200)
staff = body["accessToken"]
customers = []
for i in range(2):
    email = secrets.token_hex(8) + "@demo.example.test"
    password = secrets.token_hex(24)
    status, _ = request("POST", "/api/auth/register", {"email": email, "password": password})
    check("Customer " + str(i + 1) + " registration", status == 201)
    status, body = request("POST", "/api/auth/login", {"email": email, "password": password})
    check("Customer " + str(i + 1) + " login", status == 200)
    customers.append(body["accessToken"])
start = (dt.datetime.now(dt.timezone.utc) + dt.timedelta(days=1)).replace(microsecond=0)
iso = lambda value: value.isoformat().replace("+00:00", "Z")
status, body = request("POST", "/api/staff/slots", {"startsAt": iso(start)}, staff)
check("Staff publishes a future slot", status == 201)
slot = body["id"]
query = urllib.parse.urlencode({"from": iso(start-dt.timedelta(seconds=1)), "to": iso(start+dt.timedelta(hours=1))})
status, body = request("GET", "/api/slots?"+query, token=customers[0])
check("Customer finds available slot", status == 200 and any(s["id"] == slot for s in body))
barrier = threading.Barrier(8)
def competing_booking(index):
    barrier.wait(timeout=10)
    status, body = request("POST", "/api/bookings", {"slotId": slot}, customers[index % 2])
    return index, status, body
with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
    results = list(pool.map(competing_booking, range(8)))
statuses = [result[1] for result in results]
check("Eight simultaneous requests: one booking, seven conflicts", statuses.count(201) == 1 and statuses.count(409) == 7)
winner = next(result for result in results if result[1] == 201)
owner = customers[winner[0] % 2]
other = customers[1 - winner[0] % 2]
booking = winner[2]["id"]
status, _ = request("POST", "/api/bookings/"+booking+"/cancel", token=other)
check("Another customer cannot cancel the booking", status == 404)
status, _ = request("DELETE", "/api/staff/slots/"+slot, token=staff)
check("Booked slot cannot be withdrawn", status == 409)
status, body = request("POST", "/api/bookings/"+booking+"/cancel", token=owner)
check("Owner cancels the booking", status == 200 and body["cancelledAt"] is not None)
status, body = request("POST", "/api/bookings", {"slotId": slot}, other)
check("Cancellation frees the slot for another customer", status == 201)
request("POST", "/api/bookings/"+body["id"]+"/cancel", token=other)
status, _ = request("DELETE", "/api/staff/slots/"+slot, token=staff)
check("Unbooked slot can be withdrawn", status == 204)
status, _ = request("POST", "/api/bookings", {"slotId": slot}, owner)
check("Withdrawn slot cannot be booked", status == 409)
status, _ = request("GET", "/actuator/metrics", token=owner)
check("Customer cannot access staff metrics", status == 403)
status, _ = request("POST", "/api/auth/logout", token=owner)
check("Logout revokes the current token", status == 204)
status, _ = request("GET", "/api/bookings", token=owner)
check("Revoked token is rejected", status == 401)
status, spec = request("GET", "/v3/api-docs")
check("OpenAPI specification is generated", status == 200 and "/api/bookings" in spec["paths"])
if args.openapi:
    # Runtime host/port vary; a relative URL makes the captured document reusable.
    spec["servers"] = [{"url": "/"}]
    args.openapi.parent.mkdir(parents=True, exist_ok=True)
    args.openapi.write_text(json.dumps(spec, indent=2) + "\n")
summary = {"verifiedAt": iso(dt.datetime.now(dt.timezone.utc)), "checksPassed": len(checks), "bookingRace": {"requests": 8, "created": 1, "conflicts": 7}, "checks": checks}
if args.report:
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(summary, indent=2) + "\n")
print(json.dumps(summary, indent=2))
