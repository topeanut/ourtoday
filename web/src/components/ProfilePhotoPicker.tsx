"use client";

import { useEffect, useMemo, useRef } from "react";
import { CameraIcon } from "./Icons";
import { Avatar } from "./ui";

/**
 * 프로필 사진 고르기. 고른 파일은 미리보기만 하고, 업로드는 부모가 저장할 때 함
 * (가입 화면에서 사진만 올리고 그만두면 저장소에 버려진 파일이 남지 않게)
 */
export function ProfilePhotoPicker({
  name,
  seed,
  currentSrc,
  file,
  onChange,
  size = 96,
}: {
  name: string;
  seed?: string;
  currentSrc: string | null | undefined;
  file: File | null;
  onChange: (file: File) => void;
  size?: number;
}) {
  const fileRef = useRef<HTMLInputElement>(null);
  const preview = useMemo(() => (file ? URL.createObjectURL(file) : null), [file]);
  useEffect(() => () => {
    if (preview) URL.revokeObjectURL(preview);
  }, [preview]);

  return (
    <>
      <button
        type="button"
        onClick={() => fileRef.current?.click()}
        className="relative rounded-full focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-brand"
        aria-label="프로필 사진 고르기"
      >
        <Avatar name={name || "?"} seed={seed} src={preview ?? currentSrc} size={size} />
        <span className="absolute -bottom-0.5 -right-0.5 flex h-8 w-8 items-center justify-center rounded-full bg-white text-sub shadow-sm ring-1 ring-line">
          <CameraIcon className="h-4 w-4" />
        </span>
      </button>
      <input
        ref={fileRef}
        type="file"
        accept="image/*"
        hidden
        onChange={(e) => {
          const picked = e.target.files?.[0];
          if (picked) onChange(picked);
          e.target.value = "";
        }}
      />
    </>
  );
}
