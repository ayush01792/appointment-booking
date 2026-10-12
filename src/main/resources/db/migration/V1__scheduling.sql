CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE TABLE accounts (
 id uuid PRIMARY KEY, email varchar(254) NOT NULL UNIQUE,
 password_hash varchar(100) NOT NULL, role varchar(16) NOT NULL CHECK (role IN ('CUSTOMER','STAFF'))
);
CREATE TABLE sessions (
 token_hash varchar(64) PRIMARY KEY, account_id uuid NOT NULL REFERENCES accounts(id), expires_at timestamptz NOT NULL
);
CREATE INDEX sessions_expiry ON sessions(expires_at);
CREATE TABLE slots (
 id uuid PRIMARY KEY, staff_id uuid NOT NULL REFERENCES accounts(id),
 starts_at timestamptz NOT NULL, ends_at timestamptz NOT NULL,
 withdrawn boolean NOT NULL DEFAULT false,
 CONSTRAINT thirty_minute_slot CHECK (ends_at = starts_at + interval '30 minutes'),
 CONSTRAINT staff_schedule_no_overlap EXCLUDE USING gist
 (staff_id WITH =, tstzrange(starts_at, ends_at, '[)') WITH &&) WHERE (NOT withdrawn)
);
CREATE INDEX slots_start ON slots(starts_at) WHERE NOT withdrawn;
CREATE TABLE bookings (
 id uuid PRIMARY KEY, slot_id uuid NOT NULL REFERENCES slots(id), customer_id uuid NOT NULL REFERENCES accounts(id),
 created_at timestamptz NOT NULL, cancelled_at timestamptz
);
CREATE UNIQUE INDEX one_active_booking_per_slot ON bookings(slot_id) WHERE cancelled_at IS NULL;
CREATE INDEX bookings_customer ON bookings(customer_id, created_at DESC);
