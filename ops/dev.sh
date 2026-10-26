#!/usr/bin/env bash
# 개발 서버 켜기/끄기 (서버에서 실행). 메모리가 빠듯해서 QA 할 때만 켜고 끝나면 끔
#   bash ~/ourtoday/ops/dev.sh up | down | status | logs
set -euo pipefail
cd /home/ubuntu/ourtoday
COMPOSE="docker compose -f docker-compose.prod.yml -f docker-compose.image.yml -f docker-compose.dev.yml"

case "${1:-status}" in
  up)
    # 처음 한 번: 개발 DB 와 개발용 JWT 키 준비 (운영 토큰으로 개발 서버에 못 들어오게 키 분리)
    set -a; . ./.env; set +a
    docker exec ourtoday-postgres psql -U "$DB_USERNAME" -d postgres -tAc \
      "select 1 from pg_database where datname='ourtoday_dev'" | grep -q 1 \
      || docker exec ourtoday-postgres createdb -U "$DB_USERNAME" ourtoday_dev
    grep -q '^DEV_JWT_SECRET=' .env || echo "DEV_JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')" >> .env
    $COMPOSE up -d api-dev
    echo "개발 서버 시작 (1~2분 뒤 사용 가능): https://develop-ourtoday.vercel.app"
    ;;
  down)
    $COMPOSE stop api-dev && $COMPOSE rm -f api-dev
    echo "개발 서버 끔"
    ;;
  status)
    docker ps --filter name=ourtoday-api-dev --format '{{.Names}} {{.Status}}' | grep . || echo "꺼져 있음"
    ;;
  logs)
    docker logs --tail 50 ourtoday-api-dev
    ;;
  *)
    echo "사용법: dev.sh up | down | status | logs"; exit 1
    ;;
esac
