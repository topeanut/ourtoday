"use client";

import type { useRouter } from "next/navigation";

/** 앱 안에서 화면 이동이 있었는지. 공유 링크·로그인 직후처럼 첫 화면이면 뒤로가기 대신 fallback 으로 */
let lastPath: string | null = null;
let inAppNavigations = 0;

/** 경로가 실제로 바뀔 때만 셈 (개발 모드에서 effect 가 두 번 돌아도 안전) */
export function markNavigation(path: string) {
  if (path === lastPath) return;
  if (lastPath !== null) inAppNavigations++;
  lastPath = path;
}

export function goBack(router: ReturnType<typeof useRouter>, fallback = "/") {
  if (inAppNavigations > 0) router.back();
  else router.replace(fallback);
}
