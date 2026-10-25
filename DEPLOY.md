# 배포 가이드 (Oracle Cloud 상시 무료 + Vercel 프록시)

> 2026-10-25 AWS → Oracle Cloud 이관. 비용 0원 구성.
> `https://ourtoday.vercel.app` (Vercel, HTTPS) → Oracle VM `:8080` (Spring + Postgres, Docker Compose)

## 현재 인프라

| 항목 | 값 |
|------|-----|
| Oracle 계정 | 테넌시 `ljhh1648` / 사용자 `ljhh1648@gmail.com` / 홈 리전 Japan Central (Osaka, `ap-osaka-1`) |
| VM | `ourtoday-api` / VM.Standard.E2.1.Micro (AMD, 1GB + swap 2GB) / Ubuntu 24.04 / 부트 50GB |
| 공인 IP | `129.225.158.235` (임시 공인 IP — 인스턴스 종료 시 바뀜, 재부팅은 유지) |
| 네트워크 | `ourtoday-vcn` / `ourtoday-subnet` / 기본 보안 목록: 22 = 개발자 IP /32, 8080 = 전체 |
| SSH | `ssh -i ~/.ssh/ourtoday-key ubuntu@129.225.158.235` |
| 앱 경로 | `/home/ubuntu/ourtoday` (`.env` 는 서버에만) |
| Vercel | 계정 `ljhh1648@g.hongik.ac.kr` (CLI 사용자 topeanut), 프로젝트 `topeanuts-projects/ourtoday` |
| 카카오 | 앱 `오늘의 우리` (ID 1467289), Redirect URI `https://ourtoday.vercel.app/login/oauth2/code/kakao` |

> 무료 ARM(A1) 서버는 오사카 재고 부족("Out of host capacity")으로 못 만들었음. 여유 생기면 A1 로 옮기면 사양↑ + 서버 빌드 가능.

## 자동화 (2026-10-25~)

| 무엇 | 어떻게 | 확인 |
|------|--------|------|
| **자동 배포** | `main` 에 `api/**` push → GitHub Actions(`.github/workflows/deploy.yml`) 가 test·bootJar 후 `ghcr.io/buildup2026/ourtoday-api:latest` 푸시 → 서버 타이머 `ourtoday-deploy-pull`(2분) 가 새 이미지면 교체 | `journalctl -u ourtoday-deploy-pull -n 20` |
| **DB 백업** | 타이머 `ourtoday-backup` 매일 04:00 KST → 서버 `~/backups`(7일) + Object Storage `ourtoday-backup/db/`(30일) | `journalctl -u ourtoday-backup -n 5` |
| **ARM 재시도** | 타이머 `ourtoday-arm-retry` 10분마다 무료 A1(2코어/12GB) `ourtoday-arm` 생성 시도, 성공 시 `~/ARM_CREATED` 기록 후 타이머 자동 해제 → 이관은 수동 | `journalctl -u ourtoday-arm-retry -n 5`, `cat ~/ARM_CREATED` |

- 서버는 인스턴스 프린시펄(동적 그룹 `ourtoday-vm` + 정책 `ourtoday-vm-policy`)로 OCI API 호출 — 키 파일 없음
- GHCR 읽기 토큰(classic PAT, `read:packages`)으로 서버 `docker login` 되어 있음. **토큰 만료 시 자동 배포만 멈춤** → 새 토큰으로 다시 로그인
- 서버 `.env` 의 `COMPOSE_FILE=docker-compose.prod.yml:docker-compose.image.yml` 때문에 서버에서 `docker compose ...` 는 GHCR 이미지 기준으로 동작
- 타이머 재설치: `bash ~/ourtoday/ops/install-timers.sh`

## 수동 재배포 (자동 배포가 안 될 때, 저사양 서버라 jar 는 로컬에서 빌드)

```bash
# 로컬 (프로젝트 루트)
(cd api && ./gradlew bootJar)                       # → api/build/libs/app.jar
rsync -az --delete -e "ssh -i ~/.ssh/ourtoday-key" \
  --exclude build --exclude .gradle --exclude .idea --exclude bin \
  api docker-compose.prod.yml .env.example ubuntu@129.225.158.235:~/ourtoday/
rsync -az -e "ssh -i ~/.ssh/ourtoday-key" api/build/libs/app.jar \
  ubuntu@129.225.158.235:~/ourtoday/api/build/libs/app.jar
ssh -i ~/.ssh/ourtoday-key ubuntu@129.225.158.235 \
  'cd ~/ourtoday && docker compose -f docker-compose.prod.yml up -d --build'
curl https://ourtoday.vercel.app/api/v1/health      # 기동 ~1분
```

서버 `.env` 에 `API_DOCKERFILE=Dockerfile.runtime` 이 있어서 `api/Dockerfile.runtime`(jar 만 복사)로 빌드됨.

## SSH 가 타임아웃 나면

개발자 공인 IP 가 바뀐 것. Oracle 콘솔 → Cloud Shell 에서 보안 목록 22번 source 를 새 IP `/32` 로 교체:

```bash
C=$OCI_TENANCY
VCN=$(oci network vcn list -c $C --display-name ourtoday-vcn --query 'data[0].id' --raw-output)
SL=$(oci network vcn get --vcn-id $VCN --query 'data."default-security-list-id"' --raw-output)
oci network security-list update --security-list-id $SL --force --ingress-security-rules \
 '[{"source":"<새IP>/32","protocol":"6","tcpOptions":{"destinationPortRange":{"min":22,"max":22}}},
   {"source":"0.0.0.0/0","protocol":"6","tcpOptions":{"destinationPortRange":{"min":8080,"max":8080}}},
   {"source":"0.0.0.0/0","protocol":"1","icmpOptions":{"type":3,"code":4}}]'
```

## Vercel 프록시 재배포

서버 IP 가 바뀌면 `vercel-proxy/vercel.json` 수정 후 배포.
⚠️ git 저장소 안에서 배포하면 커밋 작성자 권한 문제로 `Blocked` → **git 밖 복사본에서 배포**:

```bash
rm -rf /tmp/ourtoday-proxy && cp -R vercel-proxy /tmp/ourtoday-proxy
npx vercel deploy --cwd /tmp/ourtoday-proxy --prod --yes
```

## 환경변수 (`.env`)

`.env.example` 참고. `DB_PASSWORD`, `JWT_SECRET` 은 서버에서 `openssl rand` 로 생성.
`KAKAO_CLIENT_ID` / `KAKAO_CLIENT_SECRET` = 카카오 앱 1467289 의 REST API 키 / 카카오 로그인 클라이언트 시크릿 (사용자가 직접 입력).
수정 후 `docker compose -f docker-compose.prod.yml up -d`.

## DB 백업 / 복원

```bash
# 백업 (로컬로)
ssh -i ~/.ssh/ourtoday-key ubuntu@129.225.158.235 \
  'docker exec ourtoday-postgres pg_dump -U ourtoday -d ourtoday --no-owner --clean --if-exists | gzip' \
  > ~/ourtoday-backup/ourtoday_$(date +%Y%m%d_%H%M).sql.gz
# 복원
gunzip -c <백업파일> | ssh -i ~/.ssh/ourtoday-key ubuntu@129.225.158.235 \
  'docker exec -i ourtoday-postgres psql -U ourtoday -d ourtoday -q'
```

## 비용 메모

- Oracle 상시 무료(AMD Micro VM, 부트 볼륨 50GB/200GB, 네트워크). **유료 업그레이드만 안 하면 청구 없음.** 30일 체험 크레딧이 끝나도 상시 무료 자원은 유지.
- Vercel Hobby, 카카오 무료. AWS 는 2026-10-25 전부 삭제.
- 놀고 있는 무료 VM 은 Oracle 이 회수할 수 있음 → 정기 백업 권장.
