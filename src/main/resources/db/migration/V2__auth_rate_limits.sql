CREATE TABLE auth_rate_limits (
 client_hash varchar(64) NOT NULL,
 window_start bigint NOT NULL,
 requests bigint NOT NULL CHECK (requests > 0),
 PRIMARY KEY (client_hash, window_start)
);
CREATE INDEX auth_rate_limits_window ON auth_rate_limits(window_start);
