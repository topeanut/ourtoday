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

백엔드 없이 화면만 보려면 `api` 에서 샘플 데이터로 띄운 뒤 토큰을 localStorage 에 넣는다.

```bash
cd ../api && ./gradlew bootTestRun --args='--spring.profiles.active=seed'   # build/seed-tokens.txt
```

## 로그인 흐름

1. `/login` → `/oauth2/authorization/kakao` (백엔드)
2. 카카오 → `/login/oauth2/code/kakao` (백엔드) → `OAUTH2_SUCCESS_REDIRECT_URI`(= `https://ourtoday.vercel.app/auth/callback`) `#accessToken=…&refreshToken=…`
3. `/auth/callback` 이 토큰 저장 → 온보딩 단계에 맞는 화면으로
4. 액세스 토큰(30분) 만료 시 `/api/v1/auth/refresh` 로 자동 재발급 (리프레시 30일)

## 웹뷰 브리지

`src/lib/native.ts` — 공유(`share`), 외부 브라우저(`openExternal`), 햅틱. 앱이 아니면 Web Share API / 클립보드로 대체.
User-Agent 에 `OurTodayApp` 이 있으면 앱으로 판단. 자세한 계약은 `../mobile/README.md`.

## 배포 (Vercel, 프로젝트 `topeanuts-projects/ourtoday`)

git 저장소 안에서 배포하면 커밋 작성자 권한 문제로 막혀서 복사본에서 배포한다.

```bash
rm -rf /tmp/ourtoday-web && rsync -a --exclude node_modules --exclude .next ./ /tmp/ourtoday-web/ \
  && cp -R ../vercel-proxy/.vercel /tmp/ourtoday-web/ && npx vercel deploy --cwd /tmp/ourtoday-web --prod --yes
```
