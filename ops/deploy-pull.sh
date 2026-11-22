#!/usr/bin/env bash
# 서버에서 2분마다 실행 (systemd timer). GHCR 의 latest 이미지가 바뀌었을 때만 api 컨테이너 교체
set -euo pipefail
cd /home/ubuntu/ourtoday

COMPOSE="docker compose -f docker-compose.prod.yml -f docker-compose.image.yml"
before=$(docker image inspect --format '{{.Id}}' ghcr.io/topeanut/ourtoday-api:latest 2>/dev/null || true)
$COMPOSE pull -q api
after=$(docker image inspect --format '{{.Id}}' ghcr.io/topeanut/ourtoday-api:latest)

if [ "$before" != "$after" ]; then
  echo "$(date -Is) 새 이미지 → api 교체"
  $COMPOSE up -d api
  docker image prune -f >/dev/null
fi
