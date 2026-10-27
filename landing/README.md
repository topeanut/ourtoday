# 오늘의 우리 — 앱 설치 랜딩 (Astro)

정적 사이트. 주소: https://ourtoday-app.vercel.app (Vercel 프로젝트 `ourtoday-app`)

## 구조

```
src/config.ts          사이트 설정 (아래 표)
src/layouts/Base.astro 공통 head(SEO·OG·JSON-LD)·헤더·푸터
src/pages/index.astro  랜딩 (기능·스크린샷·시작 방법·FAQ)
src/pages/privacy.astro 개인정보처리방침
src/pages/terms.astro  이용약관
src/pages/404.astro
src/styles/global.css  스타일 (Tailwind 대신 순수 CSS — Astro 5 와 설치 충돌)
public/                og.png, 파비콘, robots.txt, screens/1~4.png
scripts/make-images.swift  OG·파비콘·임시 스크린샷 생성
```

## 설정 (`src/config.ts`)

| 값 | 설명 |
|----|------|
| `SITE_URL` | 랜딩 주소. 도메인을 사면 여기와 `astro.config.mjs` 의 `site`, `public/robots.txt` 의 Sitemap 줄을 같이 바꿈 |
| `WEB_APP_URL` | "웹으로 바로 시작하기" 링크 |
| `PLAY_URL` / `PLAY_READY` | 플레이 출시 후 `PLAY_READY = true` → 버튼 활성화 |
| `CONTACT_EMAIL` | 비어 있으면 "문의처는 준비 중이에요" + 개인정보처리방침 상단 경고 표시. **출시 전 반드시 입력** |
| `NAVER_VERIFICATION` / `GOOGLE_VERIFICATION` | 검색엔진 소유 확인 값 (비어 있으면 태그 안 나감) |

## 스크린샷 교체

`public/screens/1.png ~ 4.png` 를 실제 앱 화면으로 덮어쓰기 (세로 9:16, 1080x1920 권장, 각 300KB 이하 권장).
순서: 1 홈, 2 작년 오늘, 3 버킷리스트, 4 캘린더. 캡션·대체 텍스트는 `index.astro` 의 `shots`.

## 검색 등록

**네이버 서치어드바이저** (searchadvisor.naver.com)
1. 웹마스터 도구 → 사이트 등록 → `https://ourtoday-app.vercel.app`
2. 소유 확인 → "HTML 태그" → `content="..."` 안의 값을 `NAVER_VERIFICATION` 에 넣고 배포 → 확인
3. 요청 → 사이트맵 제출 → `https://ourtoday-app.vercel.app/sitemap-index.xml`
4. 요청 → 웹 페이지 수집 → `/` 요청 (첫 수집 빨라짐)

**구글 서치 콘솔** (search.google.com/search-console)
1. 속성 추가 → "URL 접두어" → `https://ourtoday-app.vercel.app/` (vercel.app 은 DNS 인증 불가라 URL 접두어 방식)
2. 확인 방법 "HTML 태그" → content 값을 `GOOGLE_VERIFICATION` 에 넣고 배포 → 확인
3. Sitemaps → `sitemap-index.xml` 제출
4. URL 검사 → `/` → 색인 생성 요청

> 무료 `vercel.app` 주소는 나중에 도메인으로 바꾸면 검색 점수가 이전되지 않을 수 있음. 도메인을 살 계획이면 빨리 바꿀수록 좋음.

## 개발 / 배포

```bash
npm install
npm run dev      # http://localhost:4321
npm run build    # dist/
# 배포 (git 밖 복사본에서 — 커밋 작성자 권한 문제 회피)
rm -rf /tmp/ourtoday-app && rsync -a --exclude node_modules --exclude dist --exclude .astro ./ /tmp/ourtoday-app/ \
  && npx vercel deploy --cwd /tmp/ourtoday-app --prod --yes
```

Node 20 기준 Astro 5 사용 (Astro 7 은 Node 22.23+ 필요).
