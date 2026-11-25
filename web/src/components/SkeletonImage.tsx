"use client";

import { useCallback, useState } from "react";

/**
 * 사진이 받아지기 전엔 스켈레톤(Skeletons.tsx 와 같은 색·펄스)을 깔고, 로드되면 페이드인.
 * 크기는 바깥(className)이 정함 — 사진 비율을 미리 모르므로 로딩 중 높이는 호출하는 쪽이 잡아 줘야 함
 */
export function SkeletonImage({
  src,
  className = "",
  imgClassName = "",
  loadingClassName = "",
  lazy = true,
}: {
  src: string;
  /** 바깥 상자 (크기·모서리) */
  className?: string;
  /** img 자체 */
  imgClassName?: string;
  /** 로딩 중에만 바깥 상자에 붙일 클래스 (예: 높이 자리 잡기) */
  loadingClassName?: string;
  lazy?: boolean;
}) {
  const [loaded, setLoaded] = useState(false);
  // 캐시에 있으면 hydration 전에 load 가 끝나 onLoad 가 안 올 수 있음 → 붙는 순간 complete 확인
  const ref = useCallback((img: HTMLImageElement | null) => {
    if (img?.complete && img.naturalWidth > 0) setLoaded(true);
  }, []);

  return (
    <span className={`relative block overflow-hidden ${loaded ? "" : loadingClassName} ${className}`}>
      {!loaded && <span className="absolute inset-0 animate-pulse bg-black/[0.06]" aria-hidden />}
      <img
        ref={ref}
        src={src}
        alt=""
        loading={lazy ? "lazy" : undefined}
        onLoad={() => setLoaded(true)}
        // 실패해도 펄스를 멈춤 (깨진 사진에 로딩이 계속 도는 것처럼 보이지 않게)
        onError={() => setLoaded(true)}
        className={`transition-opacity duration-300 motion-reduce:transition-none ${loaded ? "opacity-100" : "opacity-0"} ${imgClassName}`}
      />
    </span>
  );
}
