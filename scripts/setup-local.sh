#!/usr/bin/env bash
set -euo pipefail
task_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$task_root"
umask 077
task_mysql="${MYSQL_BIN:-/opt/homebrew/opt/mysql@8.4/bin/mysql}"
if [[ ! -x "$task_mysql" ]]; then task_mysql="$(command -v mysql || true)"; fi
if [[ -z "$task_mysql" ]]; then echo '请先安装并启动 MySQL 8.4。'; exit 1; fi
if [[ ! -f .env ]]; then
  complete_db_password="$(openssl rand -hex 24)"
  cat > .env <<EOF
DB_USERNAME=apartment_complete
DB_PASSWORD=$complete_db_password
DB_URL='jdbc:mysql://127.0.0.1:3306/shangting_apartment_complete?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&characterEncoding=UTF-8'
SERVER_PORT=8082
REDIS_PORT=6381
DEMO_ENABLED=true
DEMO_PAYMENTS_ENABLED=true
SESSION_COOKIE_SECURE=false
EOF
fi
set -a
source .env
set +a
if [[ "$DB_USERNAME" != apartment_complete || ! "$DB_PASSWORD" =~ ^[A-Za-z0-9]+$ ]]; then
  echo '自动建库仅支持独立账户 apartment_complete 和字母数字密码；自定义账户请手动建库。'; exit 1
fi
MYSQL_PWD="${MYSQL_ADMIN_PASSWORD:-}" "$task_mysql" --protocol=TCP --host="${MYSQL_ADMIN_HOST:-127.0.0.1}" --port="${MYSQL_ADMIN_PORT:-3306}" --user="${MYSQL_ADMIN_USER:-root}" <<EOF
CREATE DATABASE IF NOT EXISTS shangting_apartment_complete CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'apartment_complete'@'localhost' IDENTIFIED BY '$DB_PASSWORD';
CREATE USER IF NOT EXISTS 'apartment_complete'@'127.0.0.1' IDENTIFIED BY '$DB_PASSWORD';
GRANT ALL PRIVILEGES ON shangting_apartment_complete.* TO 'apartment_complete'@'localhost';
GRANT ALL PRIVILEGES ON shangting_apartment_complete.* TO 'apartment_complete'@'127.0.0.1';
EOF
mkdir -p runtime/redis runtime/uploads runtime/logs
if ! command -v redis-server >/dev/null; then echo '请先安装 redis（brew install redis）。'; exit 1; fi
if redis-cli -h 127.0.0.1 -p "$REDIS_PORT" ping >/dev/null 2>&1; then
  echo "Redis 端口 $REDIS_PORT 已运行，将复用该独立服务。"
else
  redis-server --bind 127.0.0.1 --port "$REDIS_PORT" --daemonize yes \
    --dir "$task_root/runtime/redis" --appendonly yes --pidfile "$task_root/runtime/redis/redis.pid" \
    --logfile "$task_root/runtime/logs/redis.log"
fi
echo '独立数据库和 Redis 已准备好；原项目数据库未修改。'
echo '启动：bash scripts/start.sh'
