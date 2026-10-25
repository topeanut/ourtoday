#!/usr/bin/env bash
# 매일 새벽 실행 (systemd timer). DB 덤프 → 서버에 7일치 + Object Storage(ourtoday-backup)에 30일치
# Object Storage 업로드는 인스턴스 프린시펄(동적 그룹 ourtoday-vm + 정책) 사용 — 키 파일 없음
set -euo pipefail
DIR=/home/ubuntu/backups
NAME="ourtoday_$(date +%Y%m%d_%H%M).sql.gz"
mkdir -p "$DIR"

docker exec ourtoday-postgres pg_dump -U ourtoday -d ourtoday --no-owner --clean --if-exists | gzip > "$DIR/$NAME"
gzip -t "$DIR/$NAME"
find "$DIR" -name 'ourtoday_*.sql.gz' -mtime +7 -delete

if command -v oci >/dev/null; then
  oci os object put --auth instance_principal --bucket-name ourtoday-backup \
    --file "$DIR/$NAME" --name "db/$NAME" --force >/dev/null
  # 30일 지난 원격 백업 정리
  cutoff=$(date -d '30 days ago' +%Y%m%d)
  oci os object list --auth instance_principal --bucket-name ourtoday-backup --prefix db/ \
    --query 'data[].name' --raw-output --all 2>/dev/null | tr -d '[]", ' | grep . | while read -r obj; do
      d=$(echo "$obj" | sed -nE 's/.*ourtoday_([0-9]{8}).*/\1/p')
      [ -n "$d" ] && [ "$d" -lt "$cutoff" ] && oci os object delete --auth instance_principal --bucket-name ourtoday-backup --object-name "$obj" --force
    done || true
fi
echo "$(date -Is) backup ok: $NAME ($(du -h "$DIR/$NAME" | cut -f1))"
