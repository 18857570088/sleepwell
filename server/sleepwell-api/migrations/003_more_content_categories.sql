USE sleepwell;

INSERT INTO content_categories
  (category_key, name, description, survey_question_key, survey_answer_key, sort_order)
VALUES
  ('exercise', '运动相关', '白天活动不足、运动不足或运动时间不合适对应的睡眠健康教育内容', 'sleepFactors', 'low_activity', 100),
  ('tea', '喝茶相关', '浓茶、晚间喝茶或睡前喝茶对应的睡眠健康教育内容', 'teaHabit', 'any_tea', 110),
  ('smoking', '抽烟相关', '吸烟、晚间吸烟或睡前吸烟对应的睡眠健康教育内容', 'smokingHabit', 'any_smoking', 120),
  ('nap', '午睡相关', '白天睡太多、午睡过长或午睡过晚对应的睡眠健康教育内容', 'napHabit', 'any_nap', 130),
  ('stimulus_control', '睡眠刺激相关', '太早上床、夜间看时间、太用力入睡等刺激控制相关内容', 'sleepFactors', 'stimulus_control', 140),
  ('cognitive_restructuring', '认知重构相关', '睡前过度思虑、过度担心睡眠等睡眠认知重构内容', 'sleepFactors', 'cognitive_restructuring', 150)
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
  ('initial.sleepFactors.low_activity.exercise', 'initial', 'sleepFactors', 'low_activity', 'exercise', 'autoplay_video_and_send_comic', 100),
  ('initial.sleepFactors.daytime_nap.nap', 'initial', 'sleepFactors', 'daytime_nap', 'nap', 'autoplay_video_and_send_comic', 110),
  ('initial.napHabit.any_nap.nap', 'initial', 'napHabit', 'any_nap', 'nap', 'autoplay_video_and_send_comic', 111),
  ('initial.teaHabit.evening.tea', 'initial', 'teaHabit', 'evening', 'tea', 'autoplay_video_and_send_comic', 120),
  ('initial.teaHabit.before_sleep_2h.tea', 'initial', 'teaHabit', 'before_sleep_2h', 'tea', 'autoplay_video_and_send_comic', 121),
  ('initial.teaHabit.any_tea.tea', 'initial', 'teaHabit', 'any_tea', 'tea', 'autoplay_video_and_send_comic', 122),
  ('initial.smokingHabit.evening.smoking', 'initial', 'smokingHabit', 'evening', 'smoking', 'autoplay_video_and_send_comic', 130),
  ('initial.smokingHabit.before_sleep_2h.smoking', 'initial', 'smokingHabit', 'before_sleep_2h', 'smoking', 'autoplay_video_and_send_comic', 131),
  ('initial.smokingHabit.any_smoking.smoking', 'initial', 'smokingHabit', 'any_smoking', 'smoking', 'autoplay_video_and_send_comic', 132),
  ('initial.sleepFactors.too_early_bed.stimulus_control', 'initial', 'sleepFactors', 'too_early_bed', 'stimulus_control', 'autoplay_video_and_send_comic', 140),
  ('initial.sleepFactors.clock_watching.stimulus_control', 'initial', 'sleepFactors', 'clock_watching', 'stimulus_control', 'autoplay_video_and_send_comic', 141),
  ('initial.sleepFactors.try_hard_sleep.stimulus_control', 'initial', 'sleepFactors', 'try_hard_sleep', 'stimulus_control', 'autoplay_video_and_send_comic', 142),
  ('initial.sleepFactors.overthinking.cognitive', 'initial', 'sleepFactors', 'overthinking', 'cognitive_restructuring', 'autoplay_video_and_send_comic', 150),
  ('initial.sleepFactors.sleep_worry.cognitive', 'initial', 'sleepFactors', 'sleep_worry', 'cognitive_restructuring', 'autoplay_video_and_send_comic', 151),
  ('initial.therapyModule.stimulus_control', 'initial', 'therapyModule', 'stimulus_control', 'stimulus_control', 'autoplay_video_and_send_comic', 160),
  ('initial.therapyModule.cognitive_restructuring', 'initial', 'therapyModule', 'cognitive_restructuring', 'cognitive_restructuring', 'autoplay_video_and_send_comic', 170)
ON DUPLICATE KEY UPDATE
  category_key = VALUES(category_key),
  action = VALUES(action),
  status = 'active',
  sort_order = VALUES(sort_order);
