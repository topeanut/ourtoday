"use client";

import type { useRouter } from "next/navigation";

/** 앱 안에서 화면 이동이 있었는지. 공유 링크·로그인 직후처럼 첫 화면이면 뒤로가기 대신 fallback 으로 */
let inAppNavigations = 0;

export function markNavigation() {
  inAppNavigations++;
}

export function goBack(router: ReturnType<typeof useRouter>, fallback = "/") {
  if (inAppNavigations > 1) router.back();
  else router.replace(fallback);
}
