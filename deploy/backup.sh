#!/usr/bin/env bash
# ============================================================
# 自习室数据备份：停服几秒保证 H2 一致性，打包 data + .env
# 用法：sudo bash /opt/study-room/backup.sh
# 建议 cron：0 3 * * * /opt/study-room/backup.sh >> /var/log/study-room-backup.log 2>&1
# ============================================================
set -euo pipefail

APP_DIR=/opt/study-room
BACKUP_DIR="$APP_DIR/backups"
KEEP=${KEEP:-14}
STAMP=$(date +%Y%m%d-%H%M%S)
mkdir -p "$BACKUP_DIR"

echo "==> 停止服务以一致性备份（约几秒）"
systemctl stop study-room
trap 'systemctl start study-room' EXIT

echo "==> 打包 data/ 与 .env"
tar -czf "$BACKUP_DIR/data-$STAMP.tar.gz" -C "$APP_DIR" data .env

echo "==> 清理 ${KEEP} 天前的备份"
find "$BACKUP_DIR" -name 'data-*.tar.gz' -mtime +"$KEEP" -delete

echo "==> 完成：$BACKUP_DIR/data-$STAMP.tar.gz"
# 可选：异地同步（取消注释并配置远端）
# rsync -az "$BACKUP_DIR/" user@remote:/backup/study-room/
