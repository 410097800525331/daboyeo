-- daboyeo TiDB migration 006
-- R2-backed poster storage metadata. TiDB keeps searchable movie data and public poster pointers;
-- Cloudflare R2 keeps the poster image bytes.

SET NAMES utf8mb4;

ALTER TABLE movies
  ADD COLUMN IF NOT EXISTS poster_source_url VARCHAR(1024) NULL AFTER poster_url;

ALTER TABLE movies
  ADD COLUMN IF NOT EXISTS poster_r2_key VARCHAR(1024) NULL AFTER poster_source_url;

ALTER TABLE movies
  ADD COLUMN IF NOT EXISTS poster_etag VARCHAR(128) NULL AFTER poster_r2_key;

ALTER TABLE movies
  ADD COLUMN IF NOT EXISTS poster_storage_status VARCHAR(32) NOT NULL DEFAULT 'source_only' AFTER poster_etag;

ALTER TABLE movies
  ADD COLUMN IF NOT EXISTS poster_stored_at DATETIME(3) NULL AFTER poster_storage_status;

CREATE INDEX IF NOT EXISTS idx_movies_poster_r2_key
  ON movies (provider_code, poster_r2_key(191));

CREATE INDEX IF NOT EXISTS idx_movies_poster_storage_status
  ON movies (poster_storage_status, last_collected_at);

INSERT INTO schema_migrations (version, description)
VALUES ('006', 'add R2 poster storage metadata to movies')
ON DUPLICATE KEY UPDATE
  description = VALUES(description);
