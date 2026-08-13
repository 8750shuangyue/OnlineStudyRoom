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
mkdir -p "$APP_DIR"

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

echo "==> 迁移数据（本地 data/ 目录拷到服务器后放这里）"
mkdir -p "$APP_DIR/data"
if [ -d ./data ] && [ -n "$(ls -A ./data 2>/dev/null)" ]; then
    cp -r ./data/. "$APP_DIR/data/"
    echo "    已拷贝本地 data/ 数据库文件"
fi

echo "==> 安装 systemd 服务"
install -o "$APP_USER" -g "$APP_USER" -m 600 "$APP_DIR/.env" "$APP_DIR/.env"
cp "$(dirname "$0")/study-room.service" /etc/systemd/system/study-room.service
systemctl daemon-reload
systemctl enable study-room
systemctl restart study-room
sleep 3
systemctl status study-room --no-pager | head -n 12

echo
echo "==> 完成。默认直接访问 http://服务器IP:8081"
echo "    如果使用 Nginx + 域名，请按 deploy/nginx.conf 配置，并放行 80/443："
echo "      sudo ufw allow 80/tcp && sudo ufw allow 443/tcp"
echo "    注意：数据库端口（如 3306）不要对外开放。"
