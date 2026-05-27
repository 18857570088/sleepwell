CREATE DATABASE IF NOT EXISTS sleepwell CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE sleepwell;

CREATE TABLE IF NOT EXISTS app_users (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  external_user_id VARCHAR(64) NOT NULL,
  nickname VARCHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_app_users_external_user_id (external_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS device_bindings (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  device_sn VARCHAR(128) NOT NULL,
  device_name VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'online',
  firmware_version VARCHAR(64) NULL,
  last_seen_at DATETIME NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_device_bindings_device_sn (device_sn),
  KEY idx_device_bindings_user_id (user_id),
  CONSTRAINT fk_device_bindings_user_id FOREIGN KEY (user_id) REFERENCES app_users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sleep_records (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  record_date DATE NOT NULL,
  bed_time CHAR(5) NOT NULL,
  wake_time CHAR(5) NOT NULL,
  total_sleep_minutes SMALLINT NOT NULL,
  sleep_latency_minutes SMALLINT NOT NULL,
  sleep_efficiency TINYINT NOT NULL,
  awakenings TINYINT NOT NULL,
  awake_after_sleep_minutes SMALLINT NOT NULL,
  deep_sleep_minutes SMALLINT NOT NULL,
  light_sleep_minutes SMALLINT NOT NULL,
  rem_sleep_minutes SMALLINT NOT NULL,
  sleep_debt_minutes SMALLINT NOT NULL,
  score TINYINT NOT NULL,
  midpoint CHAR(5) NOT NULL,
  recommended_midpoint CHAR(5) NOT NULL,
  stability VARCHAR(32) NOT NULL,
  sleep_timing VARCHAR(32) NOT NULL,
  source VARCHAR(32) NOT NULL DEFAULT 'mock',
  raw_payload_json JSON NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sleep_records_user_date (user_id, record_date),
  KEY idx_sleep_records_record_date (record_date),
  CONSTRAINT fk_sleep_records_user_id FOREIGN KEY (user_id) REFERENCES app_users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS health_contents (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  content_id VARCHAR(64) NOT NULL,
  type VARCHAR(16) NOT NULL,
  title VARCHAR(128) NOT NULL,
  subtitle VARCHAR(128) NOT NULL,
  duration VARCHAR(32) NOT NULL,
  cover_path VARCHAR(255) NOT NULL,
  media_path VARCHAR(255) NOT NULL,
  tags_json JSON NOT NULL,
  description TEXT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'active',
  sort_order INT NOT NULL DEFAULT 100,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_health_contents_content_id (content_id),
  KEY idx_health_contents_type_status (type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
  CONSTRAINT fk_content_progress_user_id FOREIGN KEY (user_id) REFERENCES app_users(id),
  CONSTRAINT fk_content_progress_content_id FOREIGN KEY (content_id) REFERENCES health_contents(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS upload_files (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  category VARCHAR(64) NOT NULL,
  original_name VARCHAR(255) NULL,
  stored_name VARCHAR(255) NOT NULL,
  storage_path VARCHAR(512) NOT NULL,
  mime_type VARCHAR(128) NULL,
  size_bytes BIGINT UNSIGNED NOT NULL,
  sha256 CHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_upload_files_sha256 (sha256),
  KEY idx_upload_files_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO app_users (external_user_id, nickname)
VALUES ('demo', '小林')
ON DUPLICATE KEY UPDATE nickname = VALUES(nickname);

SET @demo_user_id = (SELECT id FROM app_users WHERE external_user_id = 'demo');

INSERT INTO device_bindings (user_id, device_sn, device_name, status, firmware_version, last_seen_at)
VALUES (@demo_user_id, 'PAD-DEMO-001', '睡眠监测垫测试设备', 'online', 'mock-0.1', NOW())
ON DUPLICATE KEY UPDATE
  device_name = VALUES(device_name),
  status = VALUES(status),
  firmware_version = VALUES(firmware_version),
  last_seen_at = VALUES(last_seen_at);

INSERT INTO sleep_records (
  user_id, record_date, bed_time, wake_time, total_sleep_minutes, sleep_latency_minutes,
  sleep_efficiency, awakenings, awake_after_sleep_minutes, deep_sleep_minutes,
  light_sleep_minutes, rem_sleep_minutes, sleep_debt_minutes, score, midpoint,
  recommended_midpoint, stability, sleep_timing, source
) VALUES
(@demo_user_id, '2026-05-19', '23:40', '06:05', 312, 34, 76, 3, 34, 48, 214, 50, -390, 68, '02:52', '01:20', '一般', '偏晚', 'mock'),
(@demo_user_id, '2026-05-20', '23:25', '06:28', 384, 28, 80, 2, 24, 62, 238, 84, -330, 72, '02:46', '01:18', '一般', '良好', 'mock'),
(@demo_user_id, '2026-05-21', '23:18', '06:12', 348, 25, 82, 2, 20, 58, 218, 72, -285, 74, '02:37', '01:15', '良好', '良好', 'mock'),
(@demo_user_id, '2026-05-22', '23:10', '06:55', 426, 22, 84, 1, 16, 76, 258, 92, -245, 78, '02:33', '01:10', '良好', '良好', 'mock'),
(@demo_user_id, '2026-05-23', '23:30', '06:42', 366, 24, 83, 2, 18, 64, 226, 76, -220, 77, '02:41', '01:08', '良好', '偏晚', 'mock'),
(@demo_user_id, '2026-05-24', '23:05', '07:22', 480, 18, 87, 1, 10, 92, 286, 102, -150, 82, '02:52', '01:05', '良好', '良好', 'mock'),
(@demo_user_id, '2026-05-25', '23:15', '07:00', 462, 18, 87, 1, 8, 106, 276, 80, -90, 82, '02:52', '01:00', '良好', '良好', 'mock')
ON DUPLICATE KEY UPDATE
  bed_time = VALUES(bed_time),
  wake_time = VALUES(wake_time),
  total_sleep_minutes = VALUES(total_sleep_minutes),
  sleep_latency_minutes = VALUES(sleep_latency_minutes),
  sleep_efficiency = VALUES(sleep_efficiency),
  awakenings = VALUES(awakenings),
  awake_after_sleep_minutes = VALUES(awake_after_sleep_minutes),
  deep_sleep_minutes = VALUES(deep_sleep_minutes),
  light_sleep_minutes = VALUES(light_sleep_minutes),
  rem_sleep_minutes = VALUES(rem_sleep_minutes),
  sleep_debt_minutes = VALUES(sleep_debt_minutes),
  score = VALUES(score),
  midpoint = VALUES(midpoint),
  recommended_midpoint = VALUES(recommended_midpoint),
  stability = VALUES(stability),
  sleep_timing = VALUES(sleep_timing),
  source = VALUES(source);

INSERT INTO health_contents (
  content_id, type, title, subtitle, duration, cover_path, media_path, tags_json, description, sort_order
) VALUES
('sleep_hygiene_001', 'video', '咖啡因与睡眠的关系', '睡眠卫生教育 · 3 分钟', '3分钟', 'covers/caffeine_sleep.png', 'videos/caffeine_sleep.mp4', JSON_ARRAY('睡眠卫生', '咖啡因', '入睡困难'), '解释咖啡因摄入时间、半衰期与入睡潜伏期之间的关系。', 10),
('sleep_hygiene_002', 'video', '睡前手机使用与蓝光刺激', '睡眠习惯 · 4 分钟', '4分钟', 'covers/phone_light.png', 'videos/phone_light_sleep.mp4', JSON_ARRAY('睡前行为', '蓝光', '睡眠习惯'), '帮助用户理解睡前屏幕刺激对困意、夜醒和睡眠效率的影响。', 20),
('sleep_comic_001', 'comic', '今晚不要和睡意较劲', '漫画 · 睡眠时间限制', '6页', 'covers/dont_fight_sleep.png', 'comics/dont_fight_sleep/index.json', JSON_ARRAY('漫画', '睡眠时间限制', '放松'), '用漫画解释困意积累和固定起床时间的重要性。', 30),
('sleep_comic_002', 'comic', '夜醒后的正确打开方式', '漫画 · 夜醒应对', '5页', 'covers/night_wake.png', 'comics/night_wake/index.json', JSON_ARRAY('漫画', '夜醒', '睡眠习惯'), '通过情境漫画演示夜醒后减少焦虑和重新入睡的方法。', 40)
ON DUPLICATE KEY UPDATE
  type = VALUES(type),
  title = VALUES(title),
  subtitle = VALUES(subtitle),
  duration = VALUES(duration),
  cover_path = VALUES(cover_path),
  media_path = VALUES(media_path),
  tags_json = VALUES(tags_json),
  description = VALUES(description),
  status = 'active',
  sort_order = VALUES(sort_order);
