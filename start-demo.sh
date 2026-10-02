#!/usr/bin/env bash
# Builds everything and starts the whole demo in Docker. Needs Docker, Java 21 and Node 22.22+ (or 24).
# Usage: ./start-demo.sh [--slack]
set -euo pipefail
cd "$(dirname "$0")"

[ -f .env ] || { cp .env.example .env; echo "Created .env from .env.example: add your Anthropic API key there."; }

mvn -q -B package -DskipTests
(cd shop-ui && npm ci --no-audit --no-fund && npm run build)

profiles=()
[ "${1:-}" = "--slack" ] && profiles=(--profile slack)
docker compose "${profiles[@]}" up -d --build

echo
echo "Shop and operations console: http://localhost:8080"
echo "Traces (Jaeger):             http://localhost:16686"
