-- Movie Events table for storing crawled event data
CREATE TABLE movie_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source VARCHAR(50) NOT NULL,
    category VARCHAR(20) NOT NULL,
    title VARCHAR(500) NOT NULL,
    image_url VARCHAR(1000),
    start_date DATE,
    end_date DATE,
    d_day VARCHAR(50),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_title_start_category (title(255), start_date, category),
    INDEX idx_category (category),
    INDEX idx_source (source),
    INDEX idx_created_at (created_at)
);
