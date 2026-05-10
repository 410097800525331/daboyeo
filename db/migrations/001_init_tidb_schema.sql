-- daboyeo TiDB v1 schema
-- Search/compare fields stay normalized, provider-specific payloads stay in JSON.
-- Never store TiDB credentials or connection strings in this file.

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS schema_migrations (
  version VARCHAR(64) NOT NULL,
  description VARCHAR(255) NOT NULL,
  applied_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS providers (
  code VARCHAR(32) NOT NULL,
  display_name VARCHAR(100) NOT NULL,
  homepage_url VARCHAR(1024) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS movies (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  provider_code VARCHAR(32) NOT NULL,
  external_movie_id VARCHAR(128) NOT NULL,
  representative_movie_id VARCHAR(128) NULL,
  title_ko VARCHAR(255) NOT NULL,
  title_en VARCHAR(255) NULL,
  age_rating VARCHAR(64) NULL,
  runtime_minutes INT UNSIGNED NULL,
  release_date DATE NULL,
  booking_rate DECIMAL(7,3) NULL,
  box_office_rank INT UNSIGNED NULL,
  poster_url VARCHAR(1024) NULL,
  raw_json JSON NULL,
  first_collected_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  last_collected_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_movies_provider_external (provider_code, external_movie_id),
  KEY idx_movies_title_ko (title_ko),
  KEY idx_movies_representative (provider_code, representative_movie_id),
  KEY idx_movies_last_collected (last_collected_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS theaters (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  provider_code VARCHAR(32) NOT NULL,
  external_theater_id VARCHAR(128) NOT NULL,
  name VARCHAR(255) NOT NULL,
  region_code VARCHAR(128) NULL,
  region_name VARCHAR(255) NULL,
  address VARCHAR(1024) NULL,
  latitude DECIMAL(10,7) NULL,
  longitude DECIMAL(10,7) NULL,
  raw_json JSON NULL,
  first_collected_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  last_collected_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_theaters_provider_external (provider_code, external_theater_id),
  KEY idx_theaters_name (name),
  KEY idx_theaters_region (provider_code, region_code, region_name),
  KEY idx_theaters_last_collected (last_collected_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS screens (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  provider_code VARCHAR(32) NOT NULL,
  theater_id BIGINT UNSIGNED NULL,
  external_theater_id VARCHAR(128) NOT NULL,
  external_screen_id VARCHAR(128) NOT NULL,
  name VARCHAR(255) NOT NULL,
  screen_type VARCHAR(255) NULL,
  floor_name VARCHAR(100) NULL,
  total_seat_count INT UNSIGNED NULL,
  raw_json JSON NULL,
  first_collected_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  last_collected_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_screens_provider_theater_screen (provider_code, external_theater_id, external_screen_id),
  KEY idx_screens_theater_id (theater_id),
  KEY idx_screens_name (provider_code, name),
  KEY idx_screens_last_collected (last_collected_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS showtimes (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  provider_code VARCHAR(32) NOT NULL,
  external_showtime_key VARCHAR(255) NOT NULL,
  movie_id BIGINT UNSIGNED NULL,
  theater_id BIGINT UNSIGNED NULL,
  screen_id BIGINT UNSIGNED NULL,
  external_movie_id VARCHAR(128) NULL,
  external_theater_id VARCHAR(128) NULL,
  external_screen_id VARCHAR(128) NULL,
  movie_title VARCHAR(255) NOT NULL,
  theater_name VARCHAR(255) NOT NULL,
  region_name VARCHAR(255) NULL,
  screen_name VARCHAR(255) NULL,
  screen_type VARCHAR(255) NULL,
  format_name VARCHAR(255) NULL,
  show_date DATE NOT NULL,
  starts_at DATETIME(3) NULL,
  ends_at DATETIME(3) NULL,
  start_time_raw VARCHAR(64) NULL,
  end_time_raw VARCHAR(64) NULL,
  total_seat_count INT UNSIGNED NULL,
  remaining_seat_count INT UNSIGNED NULL,
  remaining_seat_source VARCHAR(32) NOT NULL DEFAULT 'provider',
  booking_available VARCHAR(32) NULL,
  min_price_amount INT UNSIGNED NULL,
  currency_code CHAR(3) NOT NULL DEFAULT 'KRW',
  booking_key_json JSON NULL,
  booking_url VARCHAR(1024) NULL,
  raw_json JSON NULL,
  first_collected_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  last_collected_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_showtimes_provider_external (provider_code, external_showtime_key),
  KEY idx_showtimes_search (show_date, starts_at, movie_title, region_name),
  KEY idx_showtimes_movie (provider_code, external_movie_id),
  KEY idx_showtimes_theater (provider_code, external_theater_id),
  KEY idx_showtimes_price (show_date, min_price_amount),
  KEY idx_showtimes_remaining (show_date, remaining_seat_count),
  KEY idx_showtimes_last_collected (last_collected_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS showtime_prices (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  showtime_id BIGINT UNSIGNED NULL,
  provider_code VARCHAR(32) NOT NULL,
  external_showtime_key VARCHAR(255) NOT NULL,
  price_key VARCHAR(128) NOT NULL,
  audience_type VARCHAR(100) NULL,
  seat_type VARCHAR(100) NULL,
  screen_type VARCHAR(100) NULL,
  currency_code CHAR(3) NOT NULL DEFAULT 'KRW',
  base_price_amount INT UNSIGNED NULL,
  service_fee_amount INT UNSIGNED NULL,
  total_price_amount INT UNSIGNED NULL,
  raw_json JSON NULL,
  first_collected_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  last_collected_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_prices_showtime_key (provider_code, external_showtime_key, price_key),
  KEY idx_prices_showtime_id (showtime_id),
  KEY idx_prices_total (provider_code, total_price_amount),
  KEY idx_prices_last_collected (last_collected_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS seat_snapshots (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  showtime_id BIGINT UNSIGNED NULL,
  provider_code VARCHAR(32) NOT NULL,
  external_showtime_key VARCHAR(255) NOT NULL,
  snapshot_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  total_seat_count INT UNSIGNED NULL,
  remaining_seat_count INT UNSIGNED NULL,
  sold_seat_count INT UNSIGNED NULL,
  unavailable_seat_count INT UNSIGNED NULL,
  special_seat_count INT UNSIGNED NULL,
  raw_summary_json JSON NULL,
  PRIMARY KEY (id),
  KEY idx_snapshots_showtime_id (showtime_id, snapshot_at),
  KEY idx_snapshots_external (provider_code, external_showtime_key, snapshot_at),
  KEY idx_snapshots_snapshot_at (snapshot_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS seat_snapshot_items (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  seat_snapshot_id BIGINT UNSIGNED NOT NULL,
  seat_key VARCHAR(128) NOT NULL,
  seat_label VARCHAR(64) NULL,
  seat_row VARCHAR(32) NULL,
  seat_column VARCHAR(32) NULL,
  normalized_status VARCHAR(32) NULL,
  provider_status_code VARCHAR(64) NULL,
  seat_type VARCHAR(128) NULL,
  zone_name VARCHAR(128) NULL,
  x DECIMAL(12,3) NULL,
  y DECIMAL(12,3) NULL,
  width DECIMAL(12,3) NULL,
  height DECIMAL(12,3) NULL,
  provider_meta_json JSON NULL,
  raw_json JSON NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_seat_items_snapshot_seat (seat_snapshot_id, seat_key),
  KEY idx_seat_items_status (seat_snapshot_id, normalized_status),
  KEY idx_seat_items_label (seat_snapshot_id, seat_label)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS movie_tags (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  movie_id BIGINT UNSIGNED NULL,
  provider_code VARCHAR(32) NOT NULL,
  external_movie_id VARCHAR(128) NOT NULL,
  tag_type VARCHAR(32) NOT NULL,
  tag_value VARCHAR(64) NOT NULL,
  source VARCHAR(32) NOT NULL DEFAULT 'manual',
  confidence DECIMAL(5,4) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_movie_tags_provider_tag (provider_code, external_movie_id, tag_type, tag_value),
  KEY idx_movie_tags_movie_id (movie_id),
  KEY idx_movie_tags_lookup (tag_type, tag_value),
  KEY idx_movie_tags_source (source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

INSERT INTO providers (code, display_name, homepage_url)
VALUES
  ('CGV', 'CGV', 'https://www.cgv.co.kr'),
  ('LOTTE_CINEMA', 'Lotte Cinema', 'https://www.lottecinema.co.kr'),
  ('MEGABOX', 'Megabox', 'https://www.megabox.co.kr')
ON DUPLICATE KEY UPDATE
  display_name = VALUES(display_name),
  homepage_url = VALUES(homepage_url),
  updated_at = CURRENT_TIMESTAMP(3);

INSERT INTO schema_migrations (version, description)
VALUES ('001', 'init TiDB v1 movie search and seat snapshot schema')
ON DUPLICATE KEY UPDATE
  description = VALUES(description);
