-- daboyeo TiDB migration 002
-- Fresh Lotte/Megabox samples showed region code and occupancy metrics are useful search fields.

SET NAMES utf8mb4;

ALTER TABLE showtimes
  ADD COLUMN IF NOT EXISTS region_code VARCHAR(128) NULL AFTER region_name;

ALTER TABLE showtimes
  ADD COLUMN IF NOT EXISTS sold_seat_count INT UNSIGNED NULL AFTER remaining_seat_count;

ALTER TABLE showtimes
  ADD COLUMN IF NOT EXISTS seat_occupancy_rate DECIMAL(6,3) NULL AFTER sold_seat_count;

CREATE INDEX IF NOT EXISTS idx_showtimes_region_date
  ON showtimes (region_code, show_date, starts_at);

CREATE INDEX IF NOT EXISTS idx_showtimes_occupancy
  ON showtimes (show_date, seat_occupancy_rate);

INSERT INTO schema_migrations (version, description)
VALUES ('002', 'add showtime region code and seat occupancy search metrics')
ON DUPLICATE KEY UPDATE
  description = VALUES(description);
