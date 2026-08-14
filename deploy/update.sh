#!/usr/bin/env bash
# ============================================================
# 一键更新：用新 jar 替换并重启服务（不碰数据与配置）
# 用法：sudo bash /opt/study-room/update.sh /path/to/study-room-0.1.0-SNAPSHOT.jar
# ============================================================
set -euo pipefail

JAR_SRC="${1:?用法: sudo bash update.sh <新jar路径>}"
[ -f "$JAR_SRC" ] || { echo "jar 不存在: $JAR_SRC" >&2; exit 1; }

cp "$JAR_SRC" /opt/study-room/study-room.jar
chown studyroom:studyroom /opt/study-room/study-room.jar
chmod 644 /opt/study-room/study-room.jar

systemctl restart study-room
echo "==> 已重启，等待启动..."
sleep 10
systemctl status study-room --no-pager | head -n 5
echo "==> 健康检查:"
curl -s http://127.0.0.1:8081/actuator/health && echo
