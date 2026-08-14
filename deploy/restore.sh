#!/usr/bin/env bash
# ============================================================
# 自习室数据恢复
# 用法：sudo bash /opt/study-room/restore.sh /opt/study-room/backups/data-xxx.tar.gz
# 会停止服务，恢复后自动启动；原 .env 会备份为 .env.bak
# ============================================================
set -euo pipefail

APP_DIR=/opt/study-room
BACKUP="${1:?用法: sudo bash restore.sh <备份tar.gz>}"
[ -f "$BACKUP" ] || { echo "备份文件不存在: $BACKUP" >&2; exit 1; }

echo "==> 停止服务"
systemctl stop study-room
trap 'systemctl start study-room' EXIT

echo "==> 备份当前 .env"
cp -a "$APP_DIR/.env" "$APP_DIR/.env.bak"

echo "==> 恢复数据"
rm -rf "$APP_DIR/data"
tar -xzf "$BACKUP" -C "$APP_DIR"

echo "==> 修正属主"
chown -R studyroom:studyroom "$APP_DIR/data" "$APP_DIR/.env"

echo "==> 完成，服务已自动重启。原 .env 保留在 $APP_DIR/.env.bak"
