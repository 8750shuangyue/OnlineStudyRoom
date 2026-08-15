#!/usr/bin/env bash
# ============================================================
# 自习室数据备份：短暂停服保证 H2 一致性，打包 data + .env
# 用法：sudo bash /opt/study-room/backup.sh
# 建议 cron：17 3 * * * /opt/study-room/backup.sh >> /var/log/study-room-backup.log 2>&1
# 说明：与 healthcheck 共用锁，避免停服/启服竞争导致备份失败
# ============================================================
set -euo pipefail

LOCK=/var/lock/study-room-backup.lock
exec 9>"$LOCK"
flock -n 9 || { echo "已有备份/健康检查在进行中，跳过本次"; exit 0; }

APP_DIR=/opt/study-room
BACKUP_DIR="$APP_DIR/backups"
KEEP=${KEEP:-14}
STAMP=$(date +%Y%m%d-%H%M%S)
mkdir -p "$BACKUP_DIR"

echo "==> 停止服务以一致性备份（约几秒）"
systemctl stop study-room || true
for i in $(seq 1 30); do
    if ! systemctl is-active --quiet study-room; then
        break
    fi
    sleep 1
done
if systemctl is-active --quiet study-room; then
    echo "!! 服务未能停止，放弃本次备份（数据安全优先）"
    exit 1
fi

restore() {
    systemctl start study-room
}
trap restore EXIT

echo "==> 打包 data/ 与 .env"
tar -czf "$BACKUP_DIR/data-$STAMP.tar.gz" -C "$APP_DIR" data .env

echo "==> 清理 ${KEEP} 天前的备份"
find "$BACKUP_DIR" -name 'data-*.tar.gz' -mtime +"$KEEP" -delete

echo "==> 完成：$BACKUP_DIR/data-$STAMP.tar.gz"
# 可选：异地同步（取消注释并配置远端）
# rsync -az "$BACKUP_DIR/" user@remote:/backup/study-room/
