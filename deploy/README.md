# 部署指南（服务器端）

## 0. 前置准备

- 一台 Linux 服务器（推荐 Ubuntu 22.04 / Debian 12，2 核 4G 起步）
- JDK 21（部署脚本会自动装）
- 一个打包好的 jar：`mvn package`（已自动构建最新前端并打进 jar）
- 可选：域名 + 备案（国内服务器）、HTTPS 证书

## 1. 本地打包

```bash
mvn -B package
```

产物在 `target/study-room-0.1.0-SNAPSHOT.jar`，jar 内已包含最新前端页面，
启动后直接访问 `http://服务器IP:8081` 就是完整应用。

> 构建机器需要 Node 20+；不想在构建机上装 Node 时，可用
> `mvn -B package -Dskip.frontend=true` 跳过前端构建（此时前端为旧静态资源）。

## 2. 上传并部署

把 jar、`deploy/` 目录（或至少 `deploy.sh` + `study-room.service`）、本地 `data/` 目录一起传到服务器，然后：

```bash
sudo bash deploy/deploy.sh study-room-0.1.0-SNAPSHOT.jar
```

脚本会：装 JDK21 → 建 `/opt/study-room` → 拷贝 jar → 生成 `.env`（含随机 `JWT_SECRET`）
→ 迁移 `data/` → 安装并启动 systemd 服务。

## 3. 密钥与环境变量

`/opt/study-room/.env` 至少包含：

```bash
DEEPSEEK_API_KEY=sk-你的key
JWT_SECRET=至少32位随机字符串
```

- 未配置 `JWT_SECRET`（或用了开发默认值）应用会**拒绝启动**
- DeepSeek Key 缺失时 AI 功能会返回 401

## 4. 防火墙端口

- **简单方案**：只开 8081，直接访问 `http://IP:8081`
- **推荐方案**（Nginx）：只开 **80/443**，8081 仅内网访问
- 数据库端口（如 3306）**一律不要对外开放**

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

Nginx 会把 `/` 和 `/ws`（WebSocket）代理到本机 8081，并把真实 IP 透传给后端
（限流组件依赖 `X-Real-IP`，否则所有请求会共享一个 IP 的额度）。

## 6. HTTPS（有域名时）

```bash
sudo apt-get install -y certbot python3-certbot-nginx
sudo certbot --nginx -d your-domain.com
```

证书自动续期由 certbot 定时任务处理；前端会通过 `wss://` 自动连接 WebSocket。

## 7. 数据备份

- 核心数据：`/opt/study-room/data/`（H2 文件数据库）
- 备份：`/opt/study-room/backups/`（应用内"备份"功能生成，已自动清理旧档）
- 建议每天把这两个目录打包到异地存储

## 8. 上线验收清单

- [ ] `http://域名` 打开登录页
- [ ] 注册/登录成功
- [ ] 建房间、加入房间、房间聊天（WebSocket 通）
- [ ] 专注计时 15 分钟以上并结算
- [ ] AI 聊天、资料问答（RAG）、学习计划有正常回复
- [ ] 成就/赛季徽章页能打开，公开名片可访问
- [ ] 组队专注可发起/加入/结算
- [ ] 上传 PDF/Word 后 RAG 提问可用
- [ ] `journalctl -u study-room -f` 无异常堆栈

## 9. 常见问题

- **8081 打不开**：检查 `systemctl status study-room`，看日志是否提示 JWT_SECRET 未配置
- **一直 429**：检查 Nginx 是否透传了 `X-Real-IP`
- **HTTPS 下聊天连不上**：确认 Nginx 的 `/ws` 配置了 Upgrade 头；前端会自动切 `wss://`
- **AI 返回 401**：`/opt/study-room/.env` 里的 `DEEPSEEK_API_KEY` 是否有效
