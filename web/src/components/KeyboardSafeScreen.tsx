"use client";

import { useEffect, useRef } from "react";

/**
 * 입력 한 개짜리 전체 화면(온보딩)용 컨테이너. 키보드가 열려도 화면이 스크롤되지 않게 함
 *
 * iOS(WKWebView·Safari)는 키보드가 열려도 레이아웃 높이(100dvh)를 줄이지 않고 보이는 영역(visualViewport)만 줄임.
 * 그래서 화면 높이로 꽉 찬 페이지가 키보드 높이만큼 위아래로 밀림.
 * → 컨테이너 높이를 보이는 영역 높이에 맞추고, 밀린 화면은 맨 위로 되돌림.
 *   키보드가 열린 동안 data-keyboard="open" 을 달아서 페이지가 위 여백을 줄일 수 있게 함
 */
export function KeyboardSafeScreen({ className = "", children }: { className?: string; children: React.ReactNode }) {
  const ref = useRef<HTMLElement>(null);

  useEffect(() => {
    const el = ref.current;
    const vv = window.visualViewport;
    if (!el || !vv) return;

    const update = () => {
      el.style.height = `${vv.height}px`;
      // 키보드가 화면 높이의 15% 이상 가리면 열린 것으로 봄 (주소창 변화 같은 작은 차이는 무시)
      el.dataset.keyboard = window.innerHeight - vv.height > window.innerHeight * 0.15 ? "open" : "closed";
      if (window.scrollY !== 0 || vv.offsetTop !== 0) window.scrollTo(0, 0);
    };

    update();
    vv.addEventListener("resize", update);
    vv.addEventListener("scroll", update);
    return () => {
      vv.removeEventListener("resize", update);
      vv.removeEventListener("scroll", update);
      el.style.height = "";
    };
  }, []);

  return (
    <main ref={ref} data-keyboard="closed" className={`keyboard-safe group fixed inset-x-0 top-0 h-dvh overflow-y-auto overscroll-contain ${className}`}>
      {children}
    </main>
  );
}
