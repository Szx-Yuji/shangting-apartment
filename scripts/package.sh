#!/usr/bin/env bash
set -euo pipefail
task_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$task_root"
(cd frontend && npm ci --ignore-scripts && npm run build)
./mvnw clean verify
echo '可执行JAR位于 target/；启动前加载 .env 中的环境变量。'
