# Sleepwell API

独立服务端代码目录：`/opt/sleepwell-api`

独立配置文件：`/etc/sleepwell-api/sleepwell-api.env`

独立上传目录：`/var/lib/sleepwell-api/uploads`

独立日志目录：`/var/log/sleepwell-api`

systemd 服务：`sleepwell-api.service`

Nginx 入口：`/sleepwell-api/`

本服务器已有同 IP 主站点，因此实际部署采用独立 location snippet：

`/etc/nginx/snippets/sleepwell-api-location.conf`

并 include 到现有 IP server block 中，避免重复 `server_name 152.136.62.157` 造成匹配冲突。

主要接口：

- `GET /sleepwell-api/health`
- `GET /sleepwell-api/v1/sleep-records?days=7&user_id=demo`
- `GET /sleepwell-api/v1/health-contents`
- `GET /sleepwell-api/v1/content-progress?user_id=demo`
- `POST /sleepwell-api/v1/content-progress`
- `GET /sleepwell-api/v1/content-categories`
- `GET /sleepwell-api/v1/survey/options`
- `POST /sleepwell-api/v1/survey/recommendations`
- `GET /sleepwell-api/v1/device/status?user_id=demo`
- `POST /sleepwell-api/v1/uploads`
- `POST /sleepwell-api/v1/contents/upload`
- `DELETE /sleepwell-api/v1/health-contents/{content_id}`
- `GET /sleepwell-api/tools/upload`

内容类型目前包括：

`alcohol` 饮酒相关、`caffeine` 咖啡因相关、`phone` 睡前手机、`stress` 生活压力、
`irregular_schedule` 作息不规律、`overthinking` 睡前过度思虑、`sleep_worry` 过度担心睡眠、
`sleep_restriction` 睡眠时间限制、`night_wake` 夜醒应对、`exercise` 运动相关、`tea` 喝茶相关、
`smoking` 抽烟相关、`nap` 午睡相关、`stimulus_control` 睡眠刺激相关、
`cognitive_restructuring` 认知重构相关。

视频与漫画素材目录：

- `/var/lib/sleepwell-api/uploads/videos`
- `/var/lib/sleepwell-api/uploads/comics`
- `/var/lib/sleepwell-api/uploads/covers`

调查问卷结果与素材类型通过 `content_categories` 和 `survey_content_rules`
关联。上传工具可选择触发问题 Key，并为同一内容绑定多个触发答案 Key。
最新首次问卷示例：`drinkingHabit=yes` 或 `drinkingTimes=before_sleep` 会触发
`alcohol` 类型内容；`bedtimeBehaviors=electronics_before_bed` 会触发 `phone`
类型内容；`sleepFactors=bedtime_overthinking` 会触发睡前过度思虑/认知重构内容。
推荐接口会优先返回对应视频作为 `autoPlayVideo`，对应漫画作为 `comicsToSend`。

APP 使用云端接口时，基础地址为：

`http://152.136.62.157/sleepwell-api`
