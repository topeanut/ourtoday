# 배포 가이드 (Oracle Cloud 상시 무료 + Vercel 프록시)

> 2026-10-25 AWS → Oracle Cloud 이관. 비용 0원 구성.
> `https://ourtoday.vercel.app` (Vercel, HTTPS) → Oracle VM `:8080` (Spring + Postgres, Docker Compose)

## 현재 인프라

| 항목 | 값 |
|------|-----|
| Oracle 계정 | 개인 테넌시 / 홈 리전 Japan Central (Osaka, `ap-osaka-1`) — 계정 정보는 `DEPLOY.private.md`(git 제외) |
| VM | `ourtoday-api` / VM.Standard.E2.1.Micro (AMD, 1GB + swap 2GB) / Ubuntu 24.04 / 부트 50GB |
| 공인 IP | `$SERVER_IP` — 실제 값은 `DEPLOY.private.md` (임시 공인 IP — 인스턴스 종료 시 바뀜, 재부팅은 유지) |
| 네트워크 | `ourtoday-vcn` / `ourtoday-subnet` / 기본 보안 목록: 22 = 개발자 IP /32, 8080 = 전체 |
| SSH | `ssh -i ~/.ssh/ourtoday-key ubuntu@$SERVER_IP` |
| 앱 경로 | `/home/ubuntu/ourtoday` (`.env` 는 서버에만) |
| GitHub | `topeanut/ourtoday` (2026-11-22 조직 저장소 `BuildUp2026/build-up-be` 에서 이관), 이미지 `ghcr.io/topeanut/ourtoday-api` |
| Vercel | CLI 사용자 topeanut, 프로젝트 `topeanuts-projects/ourtoday` |
| 카카오 | 앱 `오늘의 우리` (ID 1467289), Redirect URI `https://ourtoday.vercel.app/login/oauth2/code/kakao` |

> 무료 ARM(A1) 서버는 오사카 재고 부족("Out of host capacity")으로 못 만들었음. 여유 생기면 A1 로 옮기면 사양↑ + 서버 빌드 가능.

## 자동화 (2026-10-25~)

| 무엇 | 어떻게 | 확인 |
|------|--------|------|
| **자동 배포** | `main` 에 `api/**` push → GitHub Actions(`.github/workflows/deploy.yml`) 가 test·bootJar 후 `ghcr.io/topeanut/ourtoday-api:latest` 푸시 → 서버 타이머 `ourtoday-deploy-pull`(2분) 가 새 이미지면 교체 | `journalctl -u ourtoday-deploy-pull -n 20` |
| **DB 백업** | 타이머 `ourtoday-backup` 매일 04:00 KST → 서버 `~/backups`(7일) + Object Storage `ourtoday-backup/db/`(30일) | `journalctl -u ourtoday-backup -n 5` |
| **ARM 재시도** | 타이머 `ourtoday-arm-retry` 10분마다 무료 A1(1코어/6GB — 작게 요청할수록 잘 잡힘) `ourtoday-arm` 생성 시도, 성공 시 `~/ARM_CREATED` 기록 후 타이머 자동 해제 → 이관은 수동 | `journalctl -u ourtoday-arm-retry -n 5`, `cat ~/ARM_CREATED` |

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
  api docker-compose.prod.yml .env.example ubuntu@$SERVER_IP:~/ourtoday/
rsync -az -e "ssh -i ~/.ssh/ourtoday-key" api/build/libs/app.jar \
  ubuntu@$SERVER_IP:~/ourtoday/api/build/libs/app.jar
ssh -i ~/.ssh/ourtoday-key ubuntu@$SERVER_IP \
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

## 프론트엔드 (Next.js, `web/`) — 2026-10-26~

`https://ourtoday.vercel.app` 는 이제 Next.js 앱. 백엔드 프록시는 `web/next.config.ts` rewrites 가 대신함 (`vercel-proxy/` 는 예전 프록시, 비상용으로만 보관).
배포: `main` 에 `web/**` push → Vercel Git 연동이 자동 배포 (Root Directory `web`, 환경변수 `BACKEND_ORIGIN`). 수동 배포는 `web/README.md`. 서버 `.env` 에 `OAUTH2_SUCCESS_REDIRECT_URI=https://ourtoday.vercel.app/auth/callback` 필요 (비우면 로그인 결과가 JSON 으로 보임).

## 개발 서버 (보류 — 지금은 로컬로 QA)

> 2026-10-26 결정: 서버 메모리가 빠듯해서 개발 서버 대신 **로컬**(`bash ops/local.sh`)로 개발·QA. 아래 구성은 ARM/추가 서버가 생기면 다시 사용

| 항목 | 값 |
|------|-----|
| 주소 | `https://develop-ourtoday.vercel.app` (Vercel 프로젝트 `ourtoday-develop`, 화면 상단 DEV 표시) |
| 백엔드 | 운영 서버의 컨테이너 `ourtoday-api-dev` (포트 8081, JVM 160MB, 컨테이너 320MB) — **필요할 때만 켬** |
| DB | 같은 Postgres 의 `ourtoday_dev` (운영 데이터와 분리), JWT 키도 분리(`DEV_JWT_SECRET`) |
| 로그인 | 카카오 대신 **임시 로그인**(이름만, `POST /api/v1/dev/login`). `dev` 프로필에서만 존재 |
| 배포 | `develop` 브랜치 push → CI 가 `:develop` 이미지 빌드 → `dev.sh up` 때 받음 |

```bash
ssh -i ~/.ssh/ourtoday-key ubuntu@$SERVER_IP 'bash ~/ourtoday/ops/dev.sh up'     # 켜기 (1~2분 뒤 사용)
ssh -i ~/.ssh/ourtoday-key ubuntu@$SERVER_IP 'bash ~/ourtoday/ops/dev.sh down'   # 끄기
```

- 켜 두면 운영도 메모리가 빠듯해짐 → QA 끝나면 끄기
- Oracle 보안 목록에 8081 수신 규칙 필요 (Docker 포트라 서버 iptables 는 무관)
- 프론트 배포: `web/` 를 복사본에서 `npx vercel deploy --prod --yes --build-env NEXT_PUBLIC_DEV=1 --build-env BACKEND_ORIGIN=http://$SERVER_IP:8081` (복사본 이름 `ourtoday-develop`)

## 모바일 앱 (`mobile/`)

Android/iOS 웹뷰 껍데기. 빌드·서명·스토어 메모는 `mobile/README.md`.

## DB 스키마 수동 변경 기록

`ddl-auto: update` 는 enum 체크 제약을 고치지 않음 → enum 값을 추가하면 운영 DB 제약을 직접 바꿔야 함.
- 2026-10-26 `couples.status` 에 `BROKEN` 추가 (커플 해제):
  `alter table couples drop constraint couples_status_check; alter table couples add constraint couples_status_check check (status in ('PENDING','CONNECTED','BROKEN'));`

## Vercel 프록시 재배포 (예전 방식, 비상용)

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

## 사진 저장소 (Oracle Object Storage, S3 호환)

| 항목 | 값 |
|------|-----|
| 버킷 | `ourtoday-images` (비공개, 앱이 발급한 임시 URL 로만 접근: 업로드 10분 / 조회 1시간) |
| endpoint | `https://axfvsij57xcq.compat.objectstorage.ap-osaka-1.oraclecloud.com` (path-style) |
| `.env` | `STORAGE_ENDPOINT`, `STORAGE_REGION=ap-osaka-1`, `STORAGE_BUCKET`, `STORAGE_ACCESS_KEY`/`STORAGE_SECRET_KEY` |
| 키 | Oracle 콘솔 > 내 프로필 > 고객 암호 키(Customer Secret Keys) — 사용자가 서버에서 직접 입력 |

- endpoint·키 중 하나라도 비면 앱은 정상 기동하고 **이미지 API 만 비활성** (2026-10-26 키 없이 endpoint 만 넣어 502 났던 사고 대응)
- 점검: `bash ~/ourtoday/ops/e2e-image.sh` — 임시 유저·커플로 업로드 URL → PUT → 게시글 첨부 → 다운로드 → 삭제까지 확인 후 임시 데이터 정리
- 자동 배포는 **이미지만** 바꿈. `docker-compose*.yml`·`ops/` 변경은 서버로 직접 복사(`rsync`) 후 `docker compose up -d`

## DB 백업 / 복원

```bash
# 백업 (로컬로)
ssh -i ~/.ssh/ourtoday-key ubuntu@$SERVER_IP \
  'docker exec ourtoday-postgres pg_dump -U ourtoday -d ourtoday --no-owner --clean --if-exists | gzip' \
  > ~/ourtoday-backup/ourtoday_$(date +%Y%m%d_%H%M).sql.gz
# 복원
gunzip -c <백업파일> | ssh -i ~/.ssh/ourtoday-key ubuntu@$SERVER_IP \
  'docker exec -i ourtoday-postgres psql -U ourtoday -d ourtoday -q'
```

## 비용 메모

- Oracle 상시 무료(AMD Micro VM, 부트 볼륨 50GB/200GB, 네트워크). **유료 업그레이드만 안 하면 청구 없음.** 30일 체험 크레딧이 끝나도 상시 무료 자원은 유지.
- Vercel Hobby, 카카오 무료. AWS 는 2026-10-25 전부 삭제.
- 놀고 있는 무료 VM 은 Oracle 이 회수할 수 있음 → 정기 백업 권장.
