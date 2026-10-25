#!/usr/bin/env bash
# 10분마다 실행 (systemd timer). 무료 ARM(A1, 2코어/12GB) 생성을 시도하고, 성공하면 타이머를 끔
# 인스턴스 프린시펄 사용. 성공 시 /home/ubuntu/ARM_CREATED 에 정보 기록 → 이후 이관은 사람이 진행
set -uo pipefail
MARK=/home/ubuntu/ARM_CREATED
[ -f "$MARK" ] && exit 0

export OCI_CLI_AUTH=instance_principal
C=$(curl -s -H "Authorization: Bearer Oracle" http://169.254.169.254/opc/v2/instance/ | python3 -c 'import sys,json;print(json.load(sys.stdin)["compartmentId"])')
SUB=$(oci network subnet list -c "$C" --display-name ourtoday-subnet --query 'data[0].id' --raw-output)
AD=$(oci iam availability-domain list -c "$C" --query 'data[0].name' --raw-output)
IMG=$(oci compute image list -c "$C" --operating-system "Canonical Ubuntu" --operating-system-version "24.04" \
  --shape VM.Standard.A1.Flex --sort-by TIMECREATED --sort-order DESC --query 'data[0].id' --raw-output)

out=$(oci compute instance launch -c "$C" --availability-domain "$AD" --shape VM.Standard.A1.Flex \
  --shape-config '{"ocpus":2,"memoryInGBs":12}' --image-id "$IMG" --subnet-id "$SUB" --assign-public-ip true \
  --display-name ourtoday-arm --ssh-authorized-keys-file /home/ubuntu/.ssh/authorized_keys \
  --user-data-file /home/ubuntu/ourtoday/ops/arm-cloud-init.sh --boot-volume-size-in-gbs 50 \
  --query 'data.id' --raw-output 2>&1)

if [[ "$out" == ocid1.instance.* ]]; then
  echo "$(date -Is) ARM 생성 성공 $out" | tee "$MARK"
  sudo systemctl disable --now ourtoday-arm-retry.timer
else
  echo "$(date -Is) 실패: $(echo "$out" | grep -o '"message": "[^"]*"' | head -1)"
fi
