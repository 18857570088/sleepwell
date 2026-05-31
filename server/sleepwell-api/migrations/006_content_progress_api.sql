USE sleepwell;

CREATE TABLE IF NOT EXISTS content_progress (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  content_id BIGINT UNSIGNED NOT NULL,
  progress_percent TINYINT NOT NULL DEFAULT 0,
  last_position_seconds INT NOT NULL DEFAULT 0,
  completed_at DATETIME NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_content_progress_user_content (user_id, content_id),
  KEY idx_content_progress_user_updated (user_id, updated_at),
  KEY idx_content_progress_content_id (content_id),
  CONSTRAINT fk_content_progress_user_id FOREIGN KEY (user_id) REFERENCES app_users(id),
  CONSTRAINT fk_content_progress_content_id FOREIGN KEY (content_id) REFERENCES health_contents(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
