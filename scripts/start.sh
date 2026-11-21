#!/usr/bin/env bash
set -euo pipefail
task_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$task_root"
if [[ ! -f .env ]]; then bash scripts/setup-local.sh; fi
set -a
source .env
set +a
mkdir -p runtime/redis runtime/uploads runtime/logs
if ! redis-cli -h "${REDIS_HOST:-127.0.0.1}" -p "${REDIS_PORT:-6381}" ping >/dev/null 2>&1; then
  if [[ "${REDIS_HOST:-127.0.0.1}" != 127.0.0.1 || "${REDIS_PORT:-6381}" != 6381 ]]; then
    echo '自定义 Redis 不可连接，请先启动该服务。'; exit 1
  fi
  redis-server --bind 127.0.0.1 --port 6381 --daemonize yes \
    --dir "$task_root/runtime/redis" --appendonly yes --pidfile "$task_root/runtime/redis/redis.pid" \
    --logfile "$task_root/runtime/logs/redis.log"
fi
# Rebuild so restarting after source edits never serves an old frontend bundle.
if [[ ! -d frontend/node_modules ]]; then
  (cd frontend && npm ci --ignore-scripts)
fi
(cd frontend && npm run build)
exec ./mvnw spring-boot:run
