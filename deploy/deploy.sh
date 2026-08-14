#!/usr/bin/env bash
# ============================================================
# 网页版自习室 - Ubuntu 22.04 / Debian 12 部署脚本
# 用法：先按 deploy/README.md 准备好 jar，再以 root 执行：
#   sudo bash deploy/deploy.sh /path/to/study-room-0.1.0-SNAPSHOT.jar
# ============================================================
set -euo pipefail

JAR_SRC="${1:?用法: sudo bash deploy.sh <jar路径>}"
APP_DIR=/opt/study-room
APP_USER=studyroom

echo "==> 安装 JDK 21（如已安装会自动跳过）"
if ! command -v java >/dev/null 2>&1 || ! java -version 2>&1 | grep -q '"21'; then
    apt-get update -y
    apt-get install -y openjdk-21-jre-headless
fi

echo "==> 准备运行目录与用户"
id -u "$APP_USER" >/dev/null 2>&1 || useradd -r -s /usr/sbin/nologin "$APP_USER"
mkdir -p "$APP_DIR/data" "$APP_DIR/logs" "$APP_DIR/backups"

echo "==> 拷贝 jar 与 .env"
cp "$JAR_SRC" "$APP_DIR/study-room.jar"
if [ ! -f "$APP_DIR/.env" ]; then
    cp .env "$APP_DIR/.env" 2>/dev/null || touch "$APP_DIR/.env"
fi

echo "==> 确保 JWT_SECRET 已配置（至少 32 位）"
if ! grep -q '^JWT_SECRET=' "$APP_DIR/.env" || [ "$(grep '^JWT_SECRET=' "$APP_DIR/.env" | cut -d= -f2 | wc -c)" -lt 33 ]; then
    NEW_SECRET=$(head -c 48 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 48)
    echo "JWT_SECRET=$NEW_SECRET" >> "$APP_DIR/.env"
    echo "    已自动生成 JWT_SECRET 并追加到 $APP_DIR/.env"
fi

if ! grep -q '^VAPID_PUBLIC_KEY=' "$APP_DIR/.env" || ! grep -q '^VAPID_PRIVATE_KEY=' "$APP_DIR/.env"; then
    echo "    [警告] .env 缺少 VAPID_PUBLIC_KEY / VAPID_PRIVATE_KEY，推送将使用内置开发密钥"
    echo "    可把本地 .env 里的密钥对复制到服务器，生成方法见 deploy/README.md"
fi

echo "==> 迁移数据（本地 data/ 目录拷到服务器后放这里）"
if [ -d ./data ] && [ -n "$(ls -A ./data 2>/dev/null)" ]; then
    cp -r ./data/. "$APP_DIR/data/"
    echo "    已拷贝本地 data/ 数据库文件"
fi

echo "==> 目录属主与权限（应用以 $APP_USER 运行，必须可写 data/logs）"
chown -R "$APP_USER":"$APP_USER" "$APP_DIR"
chmod 600 "$APP_DIR/.env"

echo "==> 安装 systemd 服务（生产 profile 开启）"
cp "$(dirname "$0")/study-room.service" /etc/systemd/system/study-room.service
systemctl daemon-reload
systemctl enable study-room
systemctl restart study-room
sleep 3
systemctl status study-room --no-pager | head -n 12

echo
echo "==> 完成。默认访问 http://服务器IP:8081"
echo "    使用 Nginx + 域名时请按 deploy/nginx.conf 配置，并放行 80/443："
echo "      sudo ufw allow 80/tcp && sudo ufw allow 443/tcp"
echo "    日志：journalctl -u study-room -f 或 /opt/study-room/logs/study-room.log"
echo "    健康：curl http://127.0.0.1:8081/actuator/health"
echo "    备份：sudo bash /opt/study-room/backup.sh（建议加 cron，见 README）"
