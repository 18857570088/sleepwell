USE sleepwell;

ALTER TABLE health_contents
  MODIFY COLUMN trigger_answer_key VARCHAR(255) NOT NULL DEFAULT '';

