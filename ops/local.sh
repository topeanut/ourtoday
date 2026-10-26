#!/usr/bin/env bash
# 로컬에서 백엔드(메모리 DB + 샘플 데이터 + 임시 로그인 + 가짜 사진 저장소) + 프론트를 함께 실행
#   bash ops/local.sh        → http://localhost:3100  (개발 로그인: minji / jiho / solo / 아무 이름)
#   Ctrl+C 로 둘 다 종료. 데이터는 메모리라 껐다 켜면 초기화
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
API_PORT=8080
WEB_PORT=3100

cleanup() { kill "$API_PID" "$WEB_PID" 2>/dev/null || true; }
trap cleanup EXIT INT TERM

cd "$ROOT/api"
./gradlew -q bootTestRun --args="--spring.profiles.active=seed,dev --server.port=$API_PORT" &
API_PID=$!

cd "$ROOT/web"
[ -d node_modules ] || npm install
BACKEND_ORIGIN="http://localhost:$API_PORT" NEXT_PUBLIC_DEV=1 npx next dev -p "$WEB_PORT" &
WEB_PID=$!

echo "백엔드 기동 대기..."
until curl -s -o /dev/null "http://localhost:$API_PORT/api/v1/health"; do sleep 2; done
echo "준비 완료 → http://localhost:$WEB_PORT  (개발 로그인: minji / jiho / solo)"
wait
