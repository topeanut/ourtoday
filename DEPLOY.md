# 배포 가이드 (EC2 1대 + Docker Compose)

> 앱(Spring Boot) + Postgres 를 EC2 한 대에서 `docker-compose.prod.yml` 로 실행.
> RDS 는 비용 때문에 사용하지 않음. 무료 플랜 종료(2026-12-24) 전 무료 플랫폼으로 이관 예정.

## 현재 인프라 (ap-northeast-2)

| 항목 | 값 |
|------|-----|
| EC2 | `ourtoday-api` / `i-0152c44a19ed950b8` / t3.micro / AL2023 / gp3 20GB / swap 2GB |
| 공인 IP | `15.164.231.50` (고정 아님 — **인스턴스 중지/시작 시 바뀜**) |
| 보안 그룹 | `ourtoday-api-sg` (`sg-0091429dd6bb98ff8`) — 22: 개발자 IP만, 8080: 전체 |
| 키 페어 | `ourtoday-key` (개인키: 로컬 `~/.ssh/ourtoday-key`) |
| 앱 경로 | `/home/ec2-user/ourtoday` (`.env` 는 서버에만 존재, git 미포함) |

## 재배포

```bash
# 로컬 (프로젝트 루트)
rsync -az --delete -e "ssh -i ~/.ssh/ourtoday-key" \
  --exclude build --exclude .gradle --exclude .idea --exclude bin \
  api docker-compose.prod.yml .env.example ec2-user@15.164.231.50:~/ourtoday/

ssh -i ~/.ssh/ourtoday-key ec2-user@15.164.231.50 \
  'cd ~/ourtoday && docker compose -f docker-compose.prod.yml up -d --build'

curl http://15.164.231.50:8080/api/v1/health
```

## 운영 명령

```bash
ssh -i ~/.ssh/ourtoday-key ec2-user@15.164.231.50
cd ~/ourtoday
docker compose -f docker-compose.prod.yml ps
docker logs -f ourtoday-api
nano .env && docker compose -f docker-compose.prod.yml up -d   # 환경변수 변경 후 반영
```

## 환경변수 (`.env`)

`.env.example` 참고. `DB_PASSWORD`, `JWT_SECRET` 은 서버에서 `openssl rand` 로 생성됨.
`KAKAO_CLIENT_ID` / `KAKAO_CLIENT_SECRET` 은 카카오 앱 `오늘의 우리`(ID 1467289) 의 REST API 키 / 카카오 로그인 클라이언트 시크릿.

## DB 백업 / 이관

```bash
# 덤프 (서버에서)
docker exec ourtoday-postgres pg_dump -U ourtoday ourtoday > backup_$(date +%F).sql
# 로컬로 가져오기
scp -i ~/.ssh/ourtoday-key ec2-user@15.164.231.50:~/backup_*.sql .
```

## HTTPS 도메인 (Vercel 프록시)

- `https://ourtoday.vercel.app` → `vercel-proxy/vercel.json` rewrites → `http://15.164.231.50:8080`
- 재배포: `cd vercel-proxy && npx vercel deploy --prod --yes` (EC2 IP 바뀌면 vercel.json 수정 후)
- Vercel 은 원래 Host 를 넘기지 않음 → 카카오 redirect URI 는 `.env` 의 `KAKAO_REDIRECT_URI` 로 명시

## 남은 작업

- [ ] 개발자 IP 변경 시 보안 그룹 22번 규칙 갱신
- [ ] 이관 전 `pg_dump` 백업

## 비용 메모

- 무료 플랜 크레딧으로 처리 (실제 청구 없음). EC2 + 공인 IPv4 + EBS ≈ 월 $15.
- 사용 안 할 땐 인스턴스 **종료(terminate)** — 중지(stop) 해도 EBS/IP 비용 일부 발생.
