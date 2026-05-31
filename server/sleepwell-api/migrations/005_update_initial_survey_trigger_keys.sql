USE sleepwell;

INSERT INTO content_categories
  (category_key, name, description, survey_question_key, survey_answer_key, sort_order)
VALUES
  ('alcohol', '饮酒相关', '饮酒习惯对应的睡眠卫生内容', 'drinkingHabit', 'yes', 10),
  ('caffeine', '咖啡因相关', '可乐、咖啡等含咖啡因饮料对应的睡眠卫生内容', 'caffeineHabit', 'yes', 20),
  ('phone', '睡前手机', '睡前看电子产品对应的睡眠卫生内容', 'bedtimeBehaviors', 'electronics_before_bed', 30),
  ('stress', '生活压力', '生活压力大、社会或工作压力导致不良睡眠时间表对应的内容', 'sleepFactors', 'life_stress,social_work_stress_schedule', 40),
  ('irregular_schedule', '作息不规律', '睡眠时间不规律、假期作息不规律、倒班或熬夜加班对应的内容', 'sleepFactors', 'irregular_sleep_time,holiday_irregular,shift_work,overtime', 50),
  ('overthinking', '睡前过度思虑', '睡前或床上过度担心和思虑对应的内容', 'sleepFactors', 'bedtime_overthinking', 60),
  ('sleep_worry', '过度担心睡眠', '过度担心睡眠对应的内容', 'sleepFactors', 'sleep_worry', 70),
  ('sleep_restriction', '睡眠时间限制', '太早上床、早上赖床或睡眠时间不规律对应的睡眠时间限制内容', 'sleepFactors', 'too_early_bed,late_wake,irregular_sleep_time', 80),
  ('night_wake', '夜醒应对', '夜间睡不着时看时间对应的夜醒应对内容', 'bedtimeBehaviors', 'clock_watching', 90),
  ('exercise', '运动相关', '白天活动不足或晚上剧烈运动对应的睡眠健康教育内容', 'sleepFactors', 'low_activity,night_exercise', 100),
  ('tea', '喝茶相关', '喝茶习惯对应的睡眠健康教育内容', 'teaHabit', 'yes', 110),
  ('smoking', '抽烟相关', '吸烟习惯对应的睡眠健康教育内容', 'smokingHabit', 'yes', 120),
  ('nap', '午睡相关', '白天睡太多或白天躺着时间太久对应的内容', 'sleepFactors', 'daytime_nap,lying_too_long', 130),
  ('stimulus_control', '睡眠刺激相关', '床上做与睡眠无关的事、半夜看时间、太用力入睡等刺激控制相关内容', 'sleepFactors', 'bed_non_sleep,clock_watching,try_hard_sleep,too_early_bed', 140),
  ('cognitive_restructuring', '认知重构相关', '睡前过度思虑、过度担心睡眠、太用力入睡等认知重构内容', 'sleepFactors', 'bedtime_overthinking,sleep_worry,try_hard_sleep', 150)
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
  ('initial.drinkingHabit.yes.alcohol', 'initial', 'drinkingHabit', 'yes', 'alcohol', 'autoplay_video_and_send_comic', 10),
  ('initial.drinkingTimes.evening.alcohol', 'initial', 'drinkingTimes', 'evening', 'alcohol', 'autoplay_video_and_send_comic', 11),
  ('initial.drinkingTimes.before_sleep.alcohol', 'initial', 'drinkingTimes', 'before_sleep', 'alcohol', 'autoplay_video_and_send_comic', 12),
  ('initial.caffeineHabit.yes.caffeine', 'initial', 'caffeineHabit', 'yes', 'caffeine', 'autoplay_video_and_send_comic', 20),
  ('initial.caffeineTimes.afternoon.caffeine', 'initial', 'caffeineTimes', 'afternoon', 'caffeine', 'autoplay_video_and_send_comic', 21),
  ('initial.caffeineTimes.evening.caffeine', 'initial', 'caffeineTimes', 'evening', 'caffeine', 'autoplay_video_and_send_comic', 22),
  ('initial.caffeineTimes.before_sleep.caffeine', 'initial', 'caffeineTimes', 'before_sleep', 'caffeine', 'autoplay_video_and_send_comic', 23),
  ('initial.bedtimeBehaviors.electronics_before_bed.phone', 'initial', 'bedtimeBehaviors', 'electronics_before_bed', 'phone', 'autoplay_video_and_send_comic', 30),
  ('initial.sleepFactors.life_stress.stress', 'initial', 'sleepFactors', 'life_stress', 'stress', 'autoplay_video_and_send_comic', 40),
  ('initial.sleepFactors.social_work_stress_schedule.stress', 'initial', 'sleepFactors', 'social_work_stress_schedule', 'stress', 'autoplay_video_and_send_comic', 41),
  ('initial.sleepFactors.irregular_sleep_time.irregular_schedule', 'initial', 'sleepFactors', 'irregular_sleep_time', 'irregular_schedule', 'autoplay_video_and_send_comic', 50),
  ('initial.sleepFactors.holiday_irregular.irregular_schedule', 'initial', 'sleepFactors', 'holiday_irregular', 'irregular_schedule', 'autoplay_video_and_send_comic', 51),
  ('initial.sleepFactors.shift_work.irregular_schedule', 'initial', 'sleepFactors', 'shift_work', 'irregular_schedule', 'autoplay_video_and_send_comic', 52),
  ('initial.sleepFactors.overtime.irregular_schedule', 'initial', 'sleepFactors', 'overtime', 'irregular_schedule', 'autoplay_video_and_send_comic', 53),
  ('initial.sleepFactors.bedtime_overthinking.overthinking', 'initial', 'sleepFactors', 'bedtime_overthinking', 'overthinking', 'autoplay_video_and_send_comic', 60),
  ('initial.sleepFactors.sleep_worry.sleep_worry', 'initial', 'sleepFactors', 'sleep_worry', 'sleep_worry', 'autoplay_video_and_send_comic', 70),
  ('initial.sleepFactors.too_early_bed.sleep_restriction', 'initial', 'sleepFactors', 'too_early_bed', 'sleep_restriction', 'autoplay_video_and_send_comic', 80),
  ('initial.sleepFactors.late_wake.sleep_restriction', 'initial', 'sleepFactors', 'late_wake', 'sleep_restriction', 'autoplay_video_and_send_comic', 81),
  ('initial.sleepFactors.irregular_sleep_time.sleep_restriction', 'initial', 'sleepFactors', 'irregular_sleep_time', 'sleep_restriction', 'autoplay_video_and_send_comic', 82),
  ('initial.bedtimeBehaviors.clock_watching.night_wake', 'initial', 'bedtimeBehaviors', 'clock_watching', 'night_wake', 'autoplay_video_and_send_comic', 90),
  ('initial.sleepFactors.low_activity.exercise', 'initial', 'sleepFactors', 'low_activity', 'exercise', 'autoplay_video_and_send_comic', 100),
  ('initial.sleepFactors.night_exercise.exercise', 'initial', 'sleepFactors', 'night_exercise', 'exercise', 'autoplay_video_and_send_comic', 101),
  ('initial.teaHabit.yes.tea', 'initial', 'teaHabit', 'yes', 'tea', 'autoplay_video_and_send_comic', 110),
  ('initial.teaTimes.evening.tea', 'initial', 'teaTimes', 'evening', 'tea', 'autoplay_video_and_send_comic', 111),
  ('initial.teaTimes.before_sleep.tea', 'initial', 'teaTimes', 'before_sleep', 'tea', 'autoplay_video_and_send_comic', 112),
  ('initial.smokingHabit.yes.smoking', 'initial', 'smokingHabit', 'yes', 'smoking', 'autoplay_video_and_send_comic', 120),
  ('initial.smokingTimes.evening.smoking', 'initial', 'smokingTimes', 'evening', 'smoking', 'autoplay_video_and_send_comic', 121),
  ('initial.smokingTimes.before_sleep.smoking', 'initial', 'smokingTimes', 'before_sleep', 'smoking', 'autoplay_video_and_send_comic', 122),
  ('initial.sleepFactors.daytime_nap.nap', 'initial', 'sleepFactors', 'daytime_nap', 'nap', 'autoplay_video_and_send_comic', 130),
  ('initial.sleepFactors.lying_too_long.nap', 'initial', 'sleepFactors', 'lying_too_long', 'nap', 'autoplay_video_and_send_comic', 131),
  ('initial.sleepFactors.bed_non_sleep.stimulus_control', 'initial', 'sleepFactors', 'bed_non_sleep', 'stimulus_control', 'autoplay_video_and_send_comic', 140),
  ('initial.sleepFactors.clock_watching.stimulus_control', 'initial', 'sleepFactors', 'clock_watching', 'stimulus_control', 'autoplay_video_and_send_comic', 141),
  ('initial.sleepFactors.try_hard_sleep.stimulus_control', 'initial', 'sleepFactors', 'try_hard_sleep', 'stimulus_control', 'autoplay_video_and_send_comic', 142),
  ('initial.sleepFactors.bedtime_overthinking.cognitive', 'initial', 'sleepFactors', 'bedtime_overthinking', 'cognitive_restructuring', 'autoplay_video_and_send_comic', 150),
  ('initial.sleepFactors.sleep_worry.cognitive', 'initial', 'sleepFactors', 'sleep_worry', 'cognitive_restructuring', 'autoplay_video_and_send_comic', 151),
  ('initial.sleepFactors.try_hard_sleep.cognitive', 'initial', 'sleepFactors', 'try_hard_sleep', 'cognitive_restructuring', 'autoplay_video_and_send_comic', 152)
ON DUPLICATE KEY UPDATE
  question_key = VALUES(question_key),
  answer_key = VALUES(answer_key),
  category_key = VALUES(category_key),
  action = VALUES(action),
  status = 'active',
  sort_order = VALUES(sort_order);
