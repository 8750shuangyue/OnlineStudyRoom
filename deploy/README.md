# 部署指南（服务器端）

## 0. 前置准备

- 一台 Linux 服务器（推荐 Ubuntu 22.04 / Debian 12），2G 内存起步
- JDK 21（部署脚本会自动安装）
- 一个打包好的 jar：`mvn -B package`（已自动构建最新前端并打进 jar）
- 可选：域名 + 备案（国内服务器）、HTTPS 证书

## 1. 本地打包

```bash
mvn -B package
```

产物是 `target/study-room-0.1.0-SNAPSHOT.jar`，jar 内已包含最新前端页面，
启动后访问 `http://服务器IP:8081` 就是完整应用。

> 构建机器需要 Node 20+；不想装 Node 时可用 `mvn -B package -Dskip.frontend=true`
> 跳过前端构建（此时前端为旧静态资源）。

## 2. 上传并部署

把 jar、`deploy/` 目录（至少 `deploy.sh` + `study-room.service`）、本地 `data/` 目录一起传到服务器，然后：

```bash
sudo bash deploy/deploy.sh study-room-0.1.0-SNAPSHOT.jar
```

脚本会：装 JDK21 → 建 `/opt/study-room`（data/logs/backups）→ 拷 jar → 生成 `.env`（含随机
`JWT_SECRET`）→ 迁移 `data/` → 修正目录属主（应用以 `studyroom` 用户运行）→ 安装并启动 systemd 服务
（生产 profile 自动开启）。

## 3. 密钥与环境变量（/opt/study-room/.env）

至少包含：

```bash
DEEPSEEK_API_KEY=sk-你的key
JWT_SECRET=至少32位随机字符串
```

- 未配置 `JWT_SECRET`（或使用开发默认值）应用会**拒绝启动**
- DeepSeek Key 缺失时 AI 功能返回 401
- **Web Push**：建议把本地 `.env` 里的 `VAPID_PUBLIC_KEY` / `VAPID_PRIVATE_KEY` 复制到服务器，
  否则推送使用内置开发密钥（功能可用但不建议长期如此）
- 完整变量清单见仓库根目录 `.env.example`

## 4. 防火墙端口

- **简单方式**：只开 8081，直接访问 `http://IP:8081`
- **推荐方式**（Nginx）：只开 80/443，8081 仅内网访问
- 数据库端口一律不要对外开

```bash
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
```

## 5. Nginx 反代（推荐）

```bash
sudo apt-get install -y nginx
sudo cp deploy/nginx.conf /etc/nginx/conf.d/study-room.conf
# 编辑 nginx.conf，把 server_name 改成你的域名
sudo nginx -t && sudo systemctl reload nginx
```

Nginx 会把 `/` 和 `/ws`（WebSocket）代理到本机 8081，并透传真实 IP
（限流组件依赖 `X-Real-IP`/`X-Forwarded-For`，生产 profile 已开启
`server.forward-headers-strategy=framework`）。

## 6. HTTPS（有域名时）

```bash
sudo apt-get install -y certbot python3-certbot-nginx
sudo certbot --nginx -d your-domain.com
```

证书自动续期由 certbot 定时任务处理；前端会通过 `wss://` 自动连接 WebSocket。
HTTPS 后生产 profile 会发送 HSTS 头（一年，含子域）。

## 7. 监控

生产 profile 暴露以下端点：

- `GET /actuator/health` — 健康检查（liveness/readiness 探针）
- `GET /actuator/prometheus` — Prometheus 指标（默认未对外暴露，nginx 配置见注释）

常用探活：

```bash
bash /opt/study-room/healthcheck.sh
# 或公网探活：https://你的域名/actuator/health
```

`healthcheck.sh` 返回 0 表示健康；可配合 cron 或 UptimeRobot 等监控平台。

## 8. 数据备份与恢复

**备份**（停服几秒保证 H2 一致性，保留最近 14 份）：

```bash
sudo bash /opt/study-room/backup.sh
```

建议加 cron（每天凌晨 3 点）：

```bash
sudo crontab -e
# 添加：
0 3 * * * /opt/study-room/backup.sh >> /var/log/study-room-backup.log 2>&1
```

如需异地留存，在 `backup.sh` 里启用 rsync 行。

**恢复**：

```bash
sudo bash /opt/study-room/restore.sh /opt/study-room/backups/data-20260814-030001.tar.gz
```

恢复会停止服务、替换 data 目录、自动重启；原 `.env` 保留为 `.env.bak`。

> 应用内「设置 → 备份数据」可随时手动生成 H2 在线快照（不中断服务）。

## 9. 升级步骤

```bash
# 本地重新打包并上传
mvn -B package
scp target/study-room-0.1.0-SNAPSHOT.jar user@服务器:/tmp/
# 服务器上
sudo bash /opt/study-room/deploy.sh /tmp/study-room-0.1.0-SNAPSHOT.jar
```

升级不会动 `/opt/study-room/.env` 和 `data/`（脚本只在缺失时创建/迁移）。

## 10. 上线验收清单

- [ ] `https://域名` 打开登录页
- [ ] 注册/登录成功
- [ ] 建房间、加入房间、房间聊天（WebSocket 通，wss 正常）
- [ ] 专注计时 15 分钟以上并结束
- [ ] AI 聊天、资料问答（RAG）、学习计划有正常回复
- [ ] 成就/赛季徽章页能打开，公开名片可访问
- [ ] 组队专注可发起/加入/结算
- [ ] 上传 PDF/Word 后 RAG 提问可用
- [ ] 设置页开启推送通知，完成一次专注能收到浏览器通知
- [ ] `journalctl -u study-room -f` 无异常堆栈
- [ ] `curl https://域名/actuator/health` 返回 UP

## 11. 常见问题

- **8081 打不开**：`systemctl status study-room`，看日志是否提示 JWT_SECRET 未配置
- **一直 429**：检查 Nginx 是否透传 `X-Real-IP`（生产 profile 依赖它区分用户 IP）
- **HTTPS 下聊天连不上**：确认 Nginx `/ws` 配置了 Upgrade 头；前端会自动切 `wss://`
- **AI 返回 401**：`/opt/study-room/.env` 里的 `DEEPSEEK_API_KEY` 是否有效
- **页面白屏 / 控制台 CSP 报错**：如启用了安全响应头后功能异常，可在 `.env` 加
  `APP_SECURITY_HEADERS_ENABLED=false` 关闭（不建议长期关闭）
