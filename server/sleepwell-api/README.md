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
- `GET /sleepwell-api/v1/device/status?user_id=demo`
- `POST /sleepwell-api/v1/uploads`

APP 使用云端接口时，基础地址为：

`http://152.136.62.157/sleepwell-api`
