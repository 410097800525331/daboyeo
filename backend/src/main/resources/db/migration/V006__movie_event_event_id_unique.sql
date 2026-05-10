ALTER TABLE movie_events
    ADD COLUMN IF NOT EXISTS event_id VARCHAR(100) NULL,
    ADD COLUMN IF NOT EXISTS cinema VARCHAR(50) NULL;

UPDATE movie_events
SET cinema = source
WHERE cinema IS NULL OR cinema = '';

UPDATE movie_events
SET event_id = CONCAT('legacy:', id)
WHERE event_id IS NULL OR event_id = '';

ALTER TABLE movie_events
    MODIFY event_id VARCHAR(100) NOT NULL,
    MODIFY cinema VARCHAR(50) NOT NULL;

ALTER TABLE movie_events
    ADD UNIQUE KEY uk_movie_events_event_id_cinema (event_id, cinema);
