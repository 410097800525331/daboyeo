-- daboyeo TiDB migration 003
-- Optional Cloudflare R2 archive pointers. TiDB keeps searchable fields; R2 keeps large raw payloads.

SET NAMES utf8mb4;

ALTER TABLE movies
  ADD COLUMN IF NOT EXISTS raw_object_key VARCHAR(1024) NULL AFTER raw_json;

ALTER TABLE movies
  ADD COLUMN IF NOT EXISTS raw_object_etag VARCHAR(128) NULL AFTER raw_object_key;

ALTER TABLE movies
  ADD COLUMN IF NOT EXISTS raw_object_size BIGINT UNSIGNED NULL AFTER raw_object_etag;

ALTER TABLE movies
  ADD COLUMN IF NOT EXISTS raw_archived_at DATETIME(3) NULL AFTER raw_object_size;

ALTER TABLE theaters
  ADD COLUMN IF NOT EXISTS raw_object_key VARCHAR(1024) NULL AFTER raw_json;

ALTER TABLE theaters
  ADD COLUMN IF NOT EXISTS raw_object_etag VARCHAR(128) NULL AFTER raw_object_key;

ALTER TABLE theaters
  ADD COLUMN IF NOT EXISTS raw_object_size BIGINT UNSIGNED NULL AFTER raw_object_etag;

ALTER TABLE theaters
  ADD COLUMN IF NOT EXISTS raw_archived_at DATETIME(3) NULL AFTER raw_object_size;

ALTER TABLE showtimes
  ADD COLUMN IF NOT EXISTS raw_object_key VARCHAR(1024) NULL AFTER raw_json;

ALTER TABLE showtimes
  ADD COLUMN IF NOT EXISTS raw_object_etag VARCHAR(128) NULL AFTER raw_object_key;

ALTER TABLE showtimes
  ADD COLUMN IF NOT EXISTS raw_object_size BIGINT UNSIGNED NULL AFTER raw_object_etag;

ALTER TABLE showtimes
  ADD COLUMN IF NOT EXISTS raw_archived_at DATETIME(3) NULL AFTER raw_object_size;

ALTER TABLE seat_snapshots
  ADD COLUMN IF NOT EXISTS raw_object_key VARCHAR(1024) NULL AFTER raw_summary_json;

ALTER TABLE seat_snapshots
  ADD COLUMN IF NOT EXISTS raw_object_etag VARCHAR(128) NULL AFTER raw_object_key;

ALTER TABLE seat_snapshots
  ADD COLUMN IF NOT EXISTS raw_object_size BIGINT UNSIGNED NULL AFTER raw_object_etag;

ALTER TABLE seat_snapshots
  ADD COLUMN IF NOT EXISTS raw_archived_at DATETIME(3) NULL AFTER raw_object_size;

CREATE INDEX IF NOT EXISTS idx_movies_raw_object_key
  ON movies (provider_code, raw_object_key(191));

CREATE INDEX IF NOT EXISTS idx_theaters_raw_object_key
  ON theaters (provider_code, raw_object_key(191));

CREATE INDEX IF NOT EXISTS idx_showtimes_raw_object_key
  ON showtimes (provider_code, raw_object_key(191));

CREATE INDEX IF NOT EXISTS idx_snapshots_raw_object_key
  ON seat_snapshots (provider_code, raw_object_key(191));

INSERT INTO schema_migrations (version, description)
VALUES ('003', 'add optional R2 raw object archive pointers')
ON DUPLICATE KEY UPDATE
  description = VALUES(description);
