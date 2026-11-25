# 오늘의 우리 — 웹 (Next.js)

모바일 웹 + Android/iOS 웹뷰 앱(`../mobile`)이 함께 쓰는 프론트엔드.

- Next.js 15 (App Router) + TypeScript + Tailwind CSS 4 + SWR
- 모든 화면은 클라이언트 렌더링. 토큰은 `localStorage` (`ourtoday.token`, `ourtoday.refresh`)
- 백엔드는 `next.config.ts` rewrites 로 같은 도메인에서 프록시 (`/api`, `/oauth2`, `/login/oauth2`, swagger) → CORS·쿠키 문제 없음
- 모바일: 하단 탭바 / PC(768px 이상): 좌측 사이드바

## 실행

```bash
npm install
BACKEND_ORIGIN=http://localhost:8080 npx next dev -p 3100   # 3000 은 다른 프로젝트가 쓰는 경우가 있음
```

**한 번에 실행 (추천)**: 백엔드(메모리 DB + 샘플 데이터 + 임시 로그인 + 가짜 사진 저장소) + 프론트

```bash
bash ../ops/local.sh   # → http://localhost:3100, 개발 로그인에 minji / jiho(커플) / solo(커플 없음) / 아무 이름(새 계정)
```

데이터는 메모리라 껐다 켜면 초기화. 카카오 로그인은 로컬에서 안 됨 (운영에서 확인).

## 로그인 흐름

1. `/login` → `/oauth2/authorization/kakao` (백엔드)
2. 카카오 → `/login/oauth2/code/kakao` (백엔드) → `OAUTH2_SUCCESS_REDIRECT_URI`(= `https://ourtoday.vercel.app/auth/callback`) `#accessToken=…&refreshToken=…`
3. `/auth/callback` 이 토큰 저장 → 온보딩 단계에 맞는 화면으로
4. 액세스 토큰(30분) 만료 시 `/api/v1/auth/refresh` 로 자동 재발급 (리프레시 30일)

## 웹뷰 브리지

`src/lib/native.ts` — 공유(`share`), 외부 브라우저(`openExternal`), 햅틱. 앱이 아니면 Web Share API / 클립보드로 대체.
User-Agent 에 `OurTodayApp` 이 있으면 앱으로 판단. 자세한 계약은 `../mobile/README.md`.

## 배포 (Vercel, 프로젝트 `topeanuts-projects/ourtoday`)

**자동 배포**: GitHub `topeanut/ourtoday` 와 Git 연동됨. `main` 에 `web/**` 가 바뀐 커밋을 push 하면 운영 배포, 다른 브랜치는 Preview.
Vercel 설정: Root Directory `web`, Framework Next.js, 환경변수 `BACKEND_ORIGIN`(백엔드 주소, Production·Preview).

로컬 개발: `BACKEND_ORIGIN` 이 없으면 `http://localhost:8080` 으로 프록시. 운영 백엔드에 붙이려면 `BACKEND_ORIGIN=http://<서버IP>:8080 npm run dev`.

수동 배포가 필요할 때 (작업 폴더가 아니라 커밋된 스냅샷에서):

```bash
rm -rf /tmp/ourtoday-web && git archive origin/main web | tar -x -C /tmp && mv /tmp/web /tmp/ourtoday-web \
  && cp -R ../vercel-proxy/.vercel /tmp/ourtoday-web/ && npx vercel deploy --cwd /tmp/ourtoday-web --prod --yes
```
