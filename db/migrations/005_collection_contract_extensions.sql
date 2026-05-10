-- daboyeo TiDB migration 005
-- Team-shareable schema extensions discovered from the fresh 3-provider crawl.
-- Keep database names personal, but keep these table names and keys identical.

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS collection_runs (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  run_key VARCHAR(128) NOT NULL,
  provider_code VARCHAR(32) NULL,
  collector_name VARCHAR(128) NOT NULL,
  target_date DATE NULL,
  started_at DATETIME(3) NULL,
  finished_at DATETIME(3) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'unknown',
  output_path VARCHAR(1024) NULL,
  request_count INT UNSIGNED NULL,
  response_count INT UNSIGNED NULL,
  movie_count INT UNSIGNED NULL,
  theater_count INT UNSIGNED NULL,
  screen_count INT UNSIGNED NULL,
  showtime_count INT UNSIGNED NULL,
  seat_count INT UNSIGNED NULL,
  error_count INT UNSIGNED NULL,
  http_403_count INT UNSIGNED NULL,
  summary_json JSON NULL,
  error_json JSON NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_collection_runs_key (run_key),
  KEY idx_collection_runs_provider_date (provider_code, target_date, started_at),
  KEY idx_collection_runs_status (status, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS canonical_movies (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  canonical_key VARCHAR(255) NOT NULL,
  title_ko VARCHAR(255) NOT NULL,
  title_en VARCHAR(255) NULL,
  release_date DATE NULL,
  runtime_minutes INT UNSIGNED NULL,
  age_rating VARCHAR(64) NULL,
  poster_url VARCHAR(1024) NULL,
  merge_status VARCHAR(32) NOT NULL DEFAULT 'candidate',
  match_json JSON NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_canonical_movies_key (canonical_key),
  KEY idx_canonical_movies_title (title_ko),
  KEY idx_canonical_movies_release (release_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS movie_provider_links (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  canonical_movie_id BIGINT UNSIGNED NOT NULL,
  movie_id BIGINT UNSIGNED NULL,
  provider_code VARCHAR(32) NOT NULL,
  external_movie_id VARCHAR(128) NOT NULL,
  representative_movie_id VARCHAR(128) NULL,
  match_method VARCHAR(64) NOT NULL DEFAULT 'manual',
  confidence DECIMAL(5,4) NULL,
  notes VARCHAR(1024) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_movie_provider_links_provider_external (provider_code, external_movie_id),
  UNIQUE KEY uk_movie_provider_links_canonical_provider (canonical_movie_id, provider_code, external_movie_id),
  KEY idx_movie_provider_links_movie_id (movie_id),
  KEY idx_movie_provider_links_representative (provider_code, representative_movie_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS seat_layouts (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  provider_code VARCHAR(32) NOT NULL,
  theater_id BIGINT UNSIGNED NULL,
  screen_id BIGINT UNSIGNED NULL,
  external_theater_id VARCHAR(128) NOT NULL,
  external_screen_id VARCHAR(128) NOT NULL,
  layout_fingerprint VARCHAR(128) NOT NULL,
  layout_name VARCHAR(255) NULL,
  screen_type VARCHAR(255) NULL,
  total_seat_count INT UNSIGNED NULL,
  raw_layout_json JSON NULL,
  first_seen_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  last_seen_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_seat_layouts_provider_screen_fingerprint (
    provider_code,
    external_theater_id,
    external_screen_id,
    layout_fingerprint
  ),
  KEY idx_seat_layouts_screen_id (screen_id),
  KEY idx_seat_layouts_provider_screen (provider_code, external_theater_id, external_screen_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS seat_layout_items (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  seat_layout_id BIGINT UNSIGNED NOT NULL,
  seat_key VARCHAR(128) NOT NULL,
  seat_label VARCHAR(64) NULL,
  seat_row VARCHAR(32) NULL,
  seat_column VARCHAR(32) NULL,
  seat_type VARCHAR(128) NULL,
  zone_name VARCHAR(128) NULL,
  x DECIMAL(12,3) NULL,
  y DECIMAL(12,3) NULL,
  width DECIMAL(12,3) NULL,
  height DECIMAL(12,3) NULL,
  provider_meta_json JSON NULL,
  raw_json JSON NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_seat_layout_items_layout_seat (seat_layout_id, seat_key),
  KEY idx_seat_layout_items_label (seat_layout_id, seat_label),
  KEY idx_seat_layout_items_zone (seat_layout_id, zone_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS provider_status_codes (
  provider_code VARCHAR(32) NOT NULL,
  status_domain VARCHAR(32) NOT NULL,
  provider_status_code VARCHAR(128) NOT NULL,
  provider_status_name VARCHAR(255) NULL,
  normalized_status VARCHAR(32) NOT NULL,
  is_available BOOLEAN NULL,
  source VARCHAR(64) NOT NULL DEFAULT 'observed',
  confidence DECIMAL(5,4) NULL,
  sample_count INT UNSIGNED NULL,
  sample_json JSON NULL,
  first_seen_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  last_seen_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (provider_code, status_domain, provider_status_code),
  KEY idx_provider_status_codes_normalized (status_domain, normalized_status),
  KEY idx_provider_status_codes_seen (provider_code, last_seen_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS provider_raw_payloads (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  collection_run_id BIGINT UNSIGNED NULL,
  provider_code VARCHAR(32) NOT NULL,
  payload_scope VARCHAR(64) NOT NULL,
  external_key VARCHAR(255) NULL,
  content_hash VARCHAR(128) NULL,
  object_key VARCHAR(1024) NULL,
  object_etag VARCHAR(128) NULL,
  object_size BIGINT UNSIGNED NULL,
  captured_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  summary_json JSON NULL,
  raw_json JSON NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_provider_raw_payloads_run (collection_run_id),
  KEY idx_provider_raw_payloads_provider_scope (provider_code, payload_scope, captured_at),
  KEY idx_provider_raw_payloads_external (provider_code, external_key),
  KEY idx_provider_raw_payloads_object_key (provider_code, object_key(191)),
  KEY idx_provider_raw_payloads_hash (provider_code, content_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

INSERT INTO provider_status_codes (
  provider_code,
  status_domain,
  provider_status_code,
  provider_status_name,
  normalized_status,
  is_available,
  source,
  confidence,
  sample_count
)
VALUES
  ('CGV', 'seat', '00', 'observed_available', 'available', TRUE, 'fresh_20260427', 1.0000, 144),
  ('CGV', 'seat', '01', 'known_sold', 'sold', FALSE, 'normalizer', 0.8000, NULL),
  ('LOTTE_CINEMA', 'seat', '0', 'observed_sold', 'sold', FALSE, 'fresh_20260427', 1.0000, 140),
  ('LOTTE_CINEMA', 'seat', '50', 'observed_available', 'available', TRUE, 'fresh_20260427', 1.0000, 2),
  ('MEGABOX', 'seat', 'GERN_SELL', 'observed_available', 'available', TRUE, 'fresh_20260427', 1.0000, 102),
  ('MEGABOX', 'seat', 'SCT04', 'observed_unavailable', 'unavailable', FALSE, 'fresh_20260427', 1.0000, 14)
ON DUPLICATE KEY UPDATE
  provider_status_name = VALUES(provider_status_name),
  normalized_status = VALUES(normalized_status),
  is_available = VALUES(is_available),
  source = VALUES(source),
  confidence = VALUES(confidence),
  sample_count = VALUES(sample_count),
  last_seen_at = CURRENT_TIMESTAMP(3),
  updated_at = CURRENT_TIMESTAMP(3);

INSERT INTO schema_migrations (version, description)
VALUES ('005', 'add collection tracking, canonical movie links, seat layouts, provider status codes, and raw payload index')
ON DUPLICATE KEY UPDATE
  description = VALUES(description);
