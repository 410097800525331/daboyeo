-- daboyeo TiDB migration 004
-- Anonymous local AI recommendation profiles, runs, and feedback.

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS recommendation_profiles (
  anonymous_id VARCHAR(80) NOT NULL,
  survey_json JSON NULL,
  liked_seed_json JSON NULL,
  disliked_seed_json JSON NULL,
  tag_weights_json JSON NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (anonymous_id),
  KEY idx_recommendation_profiles_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS recommendation_runs (
  run_id VARCHAR(80) NOT NULL,
  anonymous_id VARCHAR(80) NOT NULL,
  mode VARCHAR(16) NOT NULL,
  model_name VARCHAR(255) NULL,
  request_json JSON NOT NULL,
  candidate_scores_json JSON NULL,
  ai_response_json JSON NULL,
  response_json JSON NOT NULL,
  elapsed_ms INT UNSIGNED NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (run_id),
  KEY idx_recommendation_runs_anonymous (anonymous_id, created_at),
  KEY idx_recommendation_runs_mode (mode, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS recommendation_feedback (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  run_id VARCHAR(80) NOT NULL,
  anonymous_id VARCHAR(80) NOT NULL,
  movie_id BIGINT UNSIGNED NULL,
  showtime_id BIGINT UNSIGNED NULL,
  action VARCHAR(32) NOT NULL,
  tag_delta_json JSON NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_recommendation_feedback_run (run_id),
  KEY idx_recommendation_feedback_anonymous (anonymous_id, created_at),
  KEY idx_recommendation_feedback_showtime (showtime_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

INSERT INTO schema_migrations (version, description)
VALUES ('004', 'add anonymous local AI recommendation profiles and feedback')
ON DUPLICATE KEY UPDATE
  description = VALUES(description);
