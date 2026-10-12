CREATE INDEX slots_staff_schedule ON slots(staff_id, starts_at, id);
DROP INDEX bookings_customer;
CREATE INDEX bookings_customer ON bookings(customer_id, created_at DESC, id DESC);
