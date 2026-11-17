"use client";

import { useEffect, useRef, useState } from "react";
import { useKeyboardViewport } from "@/lib/useKeyboardViewport";

/**
 * 입력이 있는 전체 화면(온보딩·글쓰기)용 컨테이너. 키보드가 열려도 화면이 밀리거나 버튼이 가려지지 않음 (useKeyboardViewport)
 * @param mobileOnly true 면 모바일 폭(768px 미만)에서만 화면에 고정. PC 는 사이드바가 있는 일반 레이아웃 그대로
 */
export function KeyboardSafeScreen({
  as: Tag = "main",
  mobileOnly = false,
  className = "",
  children,
}: {
  as?: "main" | "div";
  mobileOnly?: boolean;
  className?: string;
  children: React.ReactNode;
}) {
  const ref = useRef<HTMLElement>(null);
  const [active, setActive] = useState(!mobileOnly);

  useEffect(() => {
    if (!mobileOnly) return;
    const mq = window.matchMedia("(max-width: 767px)");
    const sync = () => setActive(mq.matches);
    sync();
    mq.addEventListener("change", sync);
    return () => mq.removeEventListener("change", sync);
  }, [mobileOnly]);

  useKeyboardViewport(ref, active, true);

  const fixed = mobileOnly
    ? "max-md:fixed max-md:inset-x-0 max-md:top-0 max-md:h-dvh max-md:overflow-y-auto max-md:overscroll-contain"
    : "fixed inset-x-0 top-0 h-dvh overflow-y-auto overscroll-contain";
  return (
    <Tag
      ref={ref as React.RefObject<HTMLDivElement>}
      data-keyboard="closed"
      className={`keyboard-safe group ${fixed} ${className}`}
    >
      {children}
    </Tag>
  );
}
