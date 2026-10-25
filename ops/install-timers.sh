#!/usr/bin/env bash
# 서버에서 한 번 실행: 자동 배포(2분) / DB 백업(매일 04:00 KST) / ARM 재시도(10분) 타이머 등록
set -euo pipefail
OPS=/home/ubuntu/ourtoday/ops
chmod +x $OPS/*.sh

unit() { # name, description, script, schedule
  sudo tee /etc/systemd/system/ourtoday-$1.service >/dev/null <<EOF
[Unit]
Description=$2
[Service]
Type=oneshot
User=ubuntu
ExecStart=$3
EOF
  sudo tee /etc/systemd/system/ourtoday-$1.timer >/dev/null <<EOF
[Unit]
Description=$2 timer
[Timer]
$4
Persistent=true
[Install]
WantedBy=timers.target
EOF
}

unit deploy-pull "ourtoday auto deploy (GHCR pull)" "$OPS/deploy-pull.sh" "OnBootSec=2min
OnUnitActiveSec=2min"
unit backup "ourtoday DB backup" "$OPS/backup.sh" "OnCalendar=*-*-* 19:00:00 UTC"
unit arm-retry "ourtoday ARM instance retry" "$OPS/arm-retry.sh" "OnBootSec=5min
OnUnitActiveSec=10min"

# arm-retry 가 성공 시 스스로 타이머를 끌 수 있게 (그 명령만 허용)
echo "ubuntu ALL=(root) NOPASSWD: /usr/bin/systemctl disable --now ourtoday-arm-retry.timer" \
  | sudo tee /etc/sudoers.d/ourtoday-arm >/dev/null
sudo chmod 440 /etc/sudoers.d/ourtoday-arm

sudo systemctl daemon-reload
for t in backup arm-retry; do sudo systemctl enable --now ourtoday-$t.timer; done
echo "deploy-pull 타이머는 GHCR 로그인 후: sudo systemctl enable --now ourtoday-deploy-pull.timer"
systemctl list-timers 'ourtoday-*' --no-pager
