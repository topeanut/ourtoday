"use client";

import { useEffect, type RefObject } from "react";

declare global {
  interface Window {
    /** iOS 앱(1.0 다음 버전부터)이 키보드 열림/닫힘을 알려 줌. 이때는 웹뷰 자체가 키보드 위까지 줄어듦 */
    __ourtodayKeyboard?: "open" | "closed";
  }
}

/**
 * 키보드가 열려도 화면이 밀리거나 버튼이 가려지지 않게 하는 훅
 *
 * - iOS 앱 새 버전: 네이티브가 웹뷰 높이를 키보드 위까지 줄이고 'ourtoday:keyboard' 이벤트로 알려 줌
 *   → 높이는 이미 맞으니 열림 표시(data-keyboard)만 달면 됨
 * - iOS 앱 1.0·Safari: 키보드가 열려도 레이아웃 높이(100dvh)는 그대로고 보이는 영역(visualViewport)만 줄어듦
 *   → 요소(position: fixed) 높이를 보이는 영역에 맞추고, 보이는 영역이 밀린 만큼 따라감
 *
 * 키보드가 열린 동안 data-keyboard="open" 을 달아서 CSS(group-data-[keyboard=open]:…)로 여백을 줄일 수 있게 함
 *
 * @param resetScroll 화면 전체를 차지하는 페이지(온보딩·글쓰기)면 true: 밀린 문서를 맨 위로 되돌림.
 *                    시트처럼 스크롤된 페이지 위에 뜨는 것은 false: 뒤 페이지 스크롤 위치를 건드리지 않음
 */
export function useKeyboardViewport(ref: RefObject<HTMLElement | null>, enabled = true, resetScroll = false) {
  useEffect(() => {
    const el = ref.current;
    const vv = window.visualViewport;
    if (!enabled || !el || !vv) return;

    const update = () => {
      // 키보드가 화면 높이의 15% 이상 가리면 열린 것으로 봄 (주소창 변화 같은 작은 차이는 무시)
      const covered = window.innerHeight - vv.height > window.innerHeight * 0.15;
      const open = window.__ourtodayKeyboard === "open" || covered;
      el.dataset.keyboard = open ? "open" : "closed";
      // 새 iOS 앱: 네이티브가 웹뷰 높이를 키보드 위까지 줄여서 h-dvh 가 이미 맞음.
      // 여기서 또 height/top 을 덮어쓰면 시트가 더 짧아지고(버튼 아래 빈틈) 포커스 중 스크롤이 막힘 → 표시만 단다.
      if (window.__ourtodayKeyboard !== undefined) {
        el.style.height = "";
        el.style.top = "";
        return;
      }
      // iOS 1.0·Safari: 레이아웃 높이는 그대로고 보이는 영역만 줄어듦 → 요소를 보이는 영역에 맞춤
      el.style.height = `${vv.height}px`;
      if (resetScroll) {
        if (window.scrollY !== 0 || vv.offsetTop !== 0) window.scrollTo(0, 0);
      } else {
        el.style.top = `${vv.offsetTop}px`;
      }
    };

    update();
    vv.addEventListener("resize", update);
    vv.addEventListener("scroll", update);
    window.addEventListener("ourtoday:keyboard", update);
    return () => {
      vv.removeEventListener("resize", update);
      vv.removeEventListener("scroll", update);
      window.removeEventListener("ourtoday:keyboard", update);
      el.style.height = "";
      el.style.top = "";
      delete el.dataset.keyboard;
    };
  }, [ref, enabled, resetScroll]);
}
