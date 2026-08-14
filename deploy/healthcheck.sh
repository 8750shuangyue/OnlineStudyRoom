#!/usr/bin/env bash
# 健康检查：curl /actuator/health，供 cron / 监控平台调用
# 用法：bash /opt/study-room/healthcheck.sh [url]
set -euo pipefail

URL="${1:-http://127.0.0.1:8081/actuator/health}"
CODE=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "$URL" || true)
BODY=$(curl -s --max-time 10 "$URL" || true)
if [ "$CODE" = "200" ] && echo "$BODY" | grep -q '"UP"'; then
    echo "OK: 服务健康"
    exit 0
fi
echo "FAIL: 服务异常 (HTTP $CODE) $BODY" >&2
exit 1
