USE sleepwell;

CREATE TABLE IF NOT EXISTS content_categories (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  category_key VARCHAR(64) NOT NULL,
  name VARCHAR(64) NOT NULL,
  description VARCHAR(255) NOT NULL DEFAULT '',
  survey_question_key VARCHAR(64) NOT NULL DEFAULT '',
  survey_answer_key VARCHAR(64) NOT NULL DEFAULT '',
  status VARCHAR(32) NOT NULL DEFAULT 'active',
  sort_order INT NOT NULL DEFAULT 100,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_content_categories_category_key (category_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS survey_content_rules (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  rule_key VARCHAR(96) NOT NULL,
  survey_key VARCHAR(64) NOT NULL DEFAULT 'initial',
  question_key VARCHAR(64) NOT NULL,
  answer_key VARCHAR(64) NOT NULL,
  category_key VARCHAR(64) NOT NULL,
  action VARCHAR(32) NOT NULL DEFAULT 'recommend',
  status VARCHAR(32) NOT NULL DEFAULT 'active',
  sort_order INT NOT NULL DEFAULT 100,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_survey_content_rules_rule_key (rule_key),
  KEY idx_survey_content_rules_question_answer (question_key, answer_key),
  KEY idx_survey_content_rules_category_key (category_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @schema_name = DATABASE();

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE health_contents ADD COLUMN category_key VARCHAR(64) NOT NULL DEFAULT '''' AFTER type',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'health_contents' AND COLUMN_NAME = 'category_key'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE health_contents ADD COLUMN trigger_question_key VARCHAR(64) NOT NULL DEFAULT '''' AFTER category_key',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'health_contents' AND COLUMN_NAME = 'trigger_question_key'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE health_contents ADD COLUMN trigger_answer_key VARCHAR(64) NOT NULL DEFAULT '''' AFTER trigger_question_key',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'health_contents' AND COLUMN_NAME = 'trigger_answer_key'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE health_contents ADD COLUMN storage_path VARCHAR(512) NOT NULL DEFAULT '''' AFTER description',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'health_contents' AND COLUMN_NAME = 'storage_path'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE health_contents ADD COLUMN file_size_bytes BIGINT UNSIGNED NOT NULL DEFAULT 0 AFTER storage_path',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'health_contents' AND COLUMN_NAME = 'file_size_bytes'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE health_contents ADD COLUMN mime_type VARCHAR(128) NOT NULL DEFAULT '''' AFTER file_size_bytes',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'health_contents' AND COLUMN_NAME = 'mime_type'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE health_contents ADD COLUMN sha256 CHAR(64) NOT NULL DEFAULT '''' AFTER mime_type',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'health_contents' AND COLUMN_NAME = 'sha256'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE health_contents ADD KEY idx_health_contents_category_type (category_key, type, status)',
    'SELECT 1')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'health_contents' AND INDEX_NAME = 'idx_health_contents_category_type'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT INTO content_categories
  (category_key, name, description, survey_question_key, survey_answer_key, sort_order)
VALUES
  ('alcohol', '饮酒相关', '饮酒习惯、晚间饮酒或睡前饮酒对应的睡眠卫生内容', 'drinkingHabit', 'any_alcohol', 10),
  ('caffeine', '咖啡因相关', '咖啡、浓茶、可乐等咖啡因摄入对应的内容', 'caffeineTimes', 'any_caffeine', 20),
  ('phone', '睡前手机', '睡前看手机、蓝光刺激与信息刺激对应的内容', 'sleepFactors', 'phone', 30),
  ('stress', '生活压力', '生活压力大、情绪紧张对应的内容', 'sleepFactors', 'stress', 40),
  ('irregular_schedule', '作息不规律', '睡眠时间不规律、假期作息不规律对应的内容', 'sleepFactors', 'irregular_schedule', 50),
  ('overthinking', '睡前过度思虑', '思虑过多、睡前过度思虑对应的内容', 'sleepFactors', 'overthinking', 60),
  ('sleep_worry', '过度担心睡眠', '过度担心睡眠、太用力入睡对应的内容', 'sleepFactors', 'sleep_worry', 70),
  ('sleep_restriction', '睡眠时间限制', '睡眠时间限制疗法解释与依从性内容', 'therapyModule', 'sleep_restriction', 80),
  ('night_wake', '夜醒应对', '夜醒后减少焦虑和重新入睡的内容', 'sleepIssue', 'night_wake', 90)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  description = VALUES(description),
  survey_question_key = VALUES(survey_question_key),
  survey_answer_key = VALUES(survey_answer_key),
  status = 'active',
  sort_order = VALUES(sort_order);

INSERT INTO survey_content_rules
  (rule_key, survey_key, question_key, answer_key, category_key, action, sort_order)
VALUES
  ('initial.drinkingHabit.evening', 'initial', 'drinkingHabit', 'evening', 'alcohol', 'autoplay_video_and_send_comic', 10),
  ('initial.drinkingHabit.before_sleep_1h', 'initial', 'drinkingHabit', 'before_sleep_1h', 'alcohol', 'autoplay_video_and_send_comic', 11),
  ('initial.caffeineTimes.afternoon', 'initial', 'caffeineTimes', 'afternoon', 'caffeine', 'autoplay_video_and_send_comic', 20),
  ('initial.caffeineTimes.evening', 'initial', 'caffeineTimes', 'evening', 'caffeine', 'autoplay_video_and_send_comic', 21),
  ('initial.caffeineTimes.before_sleep_2h', 'initial', 'caffeineTimes', 'before_sleep_2h', 'caffeine', 'autoplay_video_and_send_comic', 22),
  ('initial.sleepFactors.phone', 'initial', 'sleepFactors', 'phone', 'phone', 'autoplay_video_and_send_comic', 30),
  ('initial.sleepFactors.stress', 'initial', 'sleepFactors', 'stress', 'stress', 'autoplay_video_and_send_comic', 40),
  ('initial.sleepFactors.irregular_schedule', 'initial', 'sleepFactors', 'irregular_schedule', 'irregular_schedule', 'autoplay_video_and_send_comic', 50),
  ('initial.sleepFactors.overthinking', 'initial', 'sleepFactors', 'overthinking', 'overthinking', 'autoplay_video_and_send_comic', 60),
  ('initial.sleepFactors.sleep_worry', 'initial', 'sleepFactors', 'sleep_worry', 'sleep_worry', 'autoplay_video_and_send_comic', 70)
ON DUPLICATE KEY UPDATE
  category_key = VALUES(category_key),
  action = VALUES(action),
  status = 'active',
  sort_order = VALUES(sort_order);

UPDATE health_contents
SET category_key = 'caffeine',
    trigger_question_key = 'caffeineTimes',
    trigger_answer_key = 'any_caffeine'
WHERE content_id = 'sleep_hygiene_001';

UPDATE health_contents
SET category_key = 'phone',
    trigger_question_key = 'sleepFactors',
    trigger_answer_key = 'phone'
WHERE content_id = 'sleep_hygiene_002';

UPDATE health_contents
SET category_key = 'sleep_restriction',
    trigger_question_key = 'therapyModule',
    trigger_answer_key = 'sleep_restriction'
WHERE content_id = 'sleep_comic_001';

UPDATE health_contents
SET category_key = 'night_wake',
    trigger_question_key = 'sleepIssue',
    trigger_answer_key = 'night_wake'
WHERE content_id = 'sleep_comic_002';
