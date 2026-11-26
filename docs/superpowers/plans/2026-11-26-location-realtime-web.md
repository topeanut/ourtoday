# 실시간 위치 공유 — 웹 (Part A-2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 커플이 웹에서 위치 공유를 켜고, 상대 위치를 지도로 보고, "정확한 위치 보기"로 잠깐 실시간을 보고, 오늘 경로를 보는 화면. 서버 Part A-1 API 위에 올라간다. (실제 위치 수집·업로드는 네이티브 A-3/A-4.)

**Architecture:** `web/src/lib/location.ts`(API 클라이언트+폴링 훅) + 마이페이지 공유 토글 + 새 `/location` 화면(카카오맵, 상대 위치, 정밀 ON/OFF+타이머, 오늘 경로) + 홈 진입 카드(상대 공유 중일 때만). 기존 `kakaoMap.ts`·`api.ts`(SWR/fetcher)·`ui.tsx` 재사용.

**Tech Stack:** Next.js App Router + SWR + Tailwind, 카카오맵 JS SDK(기존 `kakaoMap.ts`).

**Spec:** `docs/superpowers/specs/2026-11-22-location-sharing-design.md` (Part A, 웹 절)

## Global Constraints

- 서버 API(Part A-1, main 반영): `PUT/GET /api/v1/location/sharing`→`SharingResponse(boolean mine, boolean partner)`; `GET /location/partner`→`PartnerLocationResponse(boolean partnerSharing, LatLng latest /*null 가능*/, string capturedAt /*ISO, null 가능*/, boolean preciseActive)`; `POST/DELETE /location/precise`→204; `GET /location/track?date=YYYY-MM-DD`→`TrackResponse(LatLng[] path, int pointCount)`. `LatLng{lat:number, lng:number}`.
- 진입점은 **상대 공유 ON일 때만 노출**(홈 카드). 공유 토글은 마이페이지에 상시(기본 꺼짐).
- 폴링: 상대 보기 화면이 열려 있을 때 느슨 20~30초, 정밀 활성 중 5~10초. 화면 벗어나면 폴링 중지.
- 정밀 타이머는 **클라이언트에서 누른 시각 기준 3분 카운트다운**(서버가 만료 권위, 3분 뒤 자동 OFF 표시). ON/OFF 토글.
- UI 이모티콘 문자 금지 — `Icons.tsx` SVG. 기존 디자인 톤 유지.
- 카카오맵은 기존 `loadKakaoMap()` 재사용(도메인은 이미 등록됨). 내 위치 `●`는 브라우저 `getCurrentPosition` 1회(표시용, 업로드 아님) — 실패 시 생략.
- 이전 버전 앱(위치 미지원)에서도 화면이 깨지지 않게(그냥 보기만, 토글/정밀은 서버가 처리).
- 린트/빌드: `cd web && npm run lint` 0 errors, `BACKEND_ORIGIN=http://localhost:8080 npm run build` 성공(웹 테스트 하네스 없음 → lint+build+수동).

## Review Focus

1. 상대 비공유인데 위치가 보이면 안 됨 → Task 3 수동 확인(상대 미공유 시 지도/최신점 숨김, 카드 미노출).
2. 정밀 ON 후 3분 지나면 타이머가 0이 되고 자동으로 느슨 폴링으로 복귀 → Task 3 타이머 로직.
3. 화면을 벗어나거나 언마운트하면 폴링 interval이 정리돼야(누수 없음) → Task 1 훅 cleanup.
4. 공유 토글이 실패(네트워크)해도 UI가 잠기지 않고 상태 롤백 → Task 2.
5. `capturedAt`이 null이거나 오래된 경우 "N분 전"이 깨지지 않고 "위치 없음"/"n시간 전" 표기 → Task 3.

---

### Task 1: 위치 API 클라이언트 + 폴링 훅 (`lib/location.ts`)

**Files:**
- Create: `web/src/lib/location.ts`
- (타입은 여기 정의하거나 `lib/types.ts`에 추가 — 기존 패턴 따름)

**Interfaces:**
- Consumes: `api`/`fetcher` (`lib/api.ts`).
- Produces:
  - 타입 `Sharing{mine:boolean; partner:boolean}`, `PartnerLocation{partnerSharing:boolean; latest:{lat:number;lng:number}|null; capturedAt:string|null; preciseActive:boolean}`, `Track{path:{lat:number;lng:number}[]; pointCount:number}`.
  - `getSharing(): Promise<Sharing>`, `setSharing(enabled:boolean): Promise<Sharing>`, `getPartner(): Promise<PartnerLocation>`, `requestPrecise(): Promise<void>`, `releasePrecise(): Promise<void>`, `getTrack(date:string): Promise<Track>`.
  - `usePartnerLocation(active:boolean, precise:boolean)` — SWR 또는 setInterval 기반 폴링 훅: active일 때만 폴링, precise면 7초 간격 아니면 25초, 언마운트/active=false 시 정리. 반환 `{data, error, refresh}`.

- [ ] **Step 1: 작성** — `location.ts`에 위 함수·타입·훅. `api<T>(path, {method, body})` 사용. 훅은 `useEffect`로 interval 설정·cleanup.
- [ ] **Step 2: 린트** — `cd web && npm run lint` → 0 errors.
- [ ] **Step 3: 커밋** — `git commit -m "feat(web): 위치 공유 API 클라이언트 + 폴링 훅"`

---

### Task 2: 마이페이지 위치 공유 토글

**Files:**
- Modify: `web/src/app/(main)/me/page.tsx` (설정에 "위치 공유" 카드/토글 추가)

**Interfaces:**
- Consumes: Task 1 `getSharing`/`setSharing`.

- [ ] **Step 1: 통합** — 마이페이지 설정 영역에 "위치 공유" 토글(기본 꺼짐, `getSharing().mine` 초기값). 켜고 끌 때 `setSharing`. 아래 설명 "상대도 켜야 서로 보여요". 실패 시 토글 상태 롤백 + 안내. 기존 알림 토글 UI 패턴 재사용.
- [ ] **Step 2: 린트/빌드** — `npm run lint` 0 + `BACKEND_ORIGIN=... npm run build` 성공.
- [ ] **Step 3: 수동 확인** — 토글 on/off → GET/PUT 반영, 네트워크 실패 시 롤백.
- [ ] **Step 4: 커밋** — `git commit -m "feat(web): 마이페이지 위치 공유 토글"`

---

### Task 3: 위치 화면 (`/location`) + 홈 진입 카드

**Files:**
- Create: `web/src/app/(main)/location/page.tsx`
- Modify: `web/src/app/(main)/page.tsx` (홈에 "상대 위치 보기" 카드 — 상대 공유 중일 때만)
- Modify: `web/src/components/Icons.tsx` (필요 시 위치/네비게이션 아이콘; 기존 MapPin 재사용 우선)

**Interfaces:**
- Consumes: Task 1 (`usePartnerLocation`, `requestPrecise`, `releasePrecise`, `getTrack`), `kakaoMap.ts`(`loadKakaoMap`).

- [ ] **Step 1: 화면** — `/location`:
  - 카카오맵: 상대 `●`(latest), 내 `●`(브라우저 getCurrentPosition 1회, 실패 시 생략). 상대 marker + "대략 위치 · {N분 전}"(capturedAt→상대 표현; null이면 "위치 없음").
  - **[정확한 위치 보기] ON/OFF 버튼 + 남은 시간 타이머**: ON 누르면 `requestPrecise()` + 3분 카운트다운 시작 + 폴링 7초로. 0 되면 자동 느슨(25초) 복귀. OFF 누르면 `releasePrecise()` + 느슨.
  - 오늘 경로: `getTrack(today)` → polyline 그리기(토글/접기).
  - 상대 공유 OFF로 바뀌면(폴링 응답 partnerSharing=false) "상대가 공유를 껐어요" 표시 + 지도 숨김.
- [ ] **Step 2: 홈 카드** — 홈에서 `getSharing()` 또는 `getPartner()`로 상대 공유 중이면 "상대 위치 보기" 카드/배너 → `/location` 링크. 아니면 미노출.
- [ ] **Step 3: 린트/빌드** — `npm run lint` 0 + build 성공.
- [ ] **Step 4: 수동 확인** — 상대 공유 시 카드·지도·최신점, 정밀 ON→타이머·빠른 폴링→3분 자동복귀, OFF, 경로 표시, 상대 미공유 시 전부 숨김.
- [ ] **Step 5: 커밋** — `git commit -m "feat(web): 위치 화면(상대 위치·정밀·경로) + 홈 진입 카드"`

---

## 사용자가 직접 할 일

- 카카오 콘솔: 지도/도메인은 이미 등록됨(추가 없음).
- 실제 상대 위치가 보이려면 네이티브(A-3/A-4)가 위치를 업로드해야 함 — 그 전엔 seed 데이터/수동 업로드로만 확인.

## 빌더에게 맡기는 것

- 지도 마커·경로선 스타일, 타이머 UI 모양, 홈 카드 배치, "N분 전" 포맷(기존 날짜 유틸 재사용).
- 폴링 간격 미세값(느슨 20~30초·정밀 5~10초 범위 내).
