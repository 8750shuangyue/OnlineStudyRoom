#!/usr/bin/env bash
# 上线验收检查（在服务器本机执行）
set -euo pipefail
B=http://127.0.0.1:8081

echo "==> 健康检查"
curl -s "$B/actuator/health"; echo

echo "==> 注册/登录验收用户"
REG=$(curl -s -X POST "$B/api/auth/register" -H 'Content-Type: application/json' \
    -d '{"username":"acceptance_test","password":"secret123"}')
TOKEN=$(echo "$REG" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
if [ -z "$TOKEN" ]; then
    echo "注册可能已存在，尝试登录"
    REG=$(curl -s -X POST "$B/api/auth/login" -H 'Content-Type: application/json' \
        -d '{"username":"acceptance_test","password":"secret123"}')
    TOKEN=$(echo "$REG" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
fi
[ -n "$TOKEN" ] || { echo "拿不到 token，验收失败"; exit 1; }
echo "token 长度: ${#TOKEN}"

echo "==> 创建房间"
curl -s -X POST "$B/api/rooms" -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/json' -d '{"name":"acceptance-room"}' | head -c 150; echo

echo "==> 房间列表"
curl -s "$B/api/rooms" -H "Authorization: Bearer $TOKEN" | head -c 150; echo

echo "==> 公开名片"
curl -s -o /dev/null -w 'http=%{http_code}\n' "$B/api/users/acceptance_test/card"

echo "==> Swagger"
curl -s -o /dev/null -w 'http=%{http_code}\n' "$B/swagger-ui/index.html"

echo "==> 密钥检查"
grep -q '^DEEPSEEK_API_KEY=sk-' /opt/study-room/.env && echo "DEEPSEEK: present" || echo "DEEPSEEK: MISSING"
grep -q '^VAPID_PUBLIC_KEY=' /opt/study-room/.env && echo "VAPID: present" || echo "VAPID: MISSING"

echo "==> 验收完成"
