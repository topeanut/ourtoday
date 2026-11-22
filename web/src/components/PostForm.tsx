"use client";

import { useEffect, useRef, useState } from "react";
import { errorMessage } from "@/lib/api";
import { todayKst } from "@/lib/date";
import { uploadImage } from "@/lib/image";
import { CloseIcon, ImageIcon } from "./Icons";
import { LocationPicker } from "./LocationPicker";
import type { PickedLocation } from "@/lib/kakaoMap";
import { useToast } from "./Toast";
import { Button, DateField } from "./ui";

const MAX_LEN = 300;
const MAX_IMAGES = 4;

type Slot = { key: string; preview: string; imageKey?: string; file?: File };

export interface PostFormValue {
  content: string;
  postedDate: string;
  imageKeys: string[];
  location: PickedLocation | null;
}

export function PostForm({
  initial,
  submitLabel,
  onSubmit,
}: {
  initial?: { content: string; postedDate: string; images: { url: string; imageKey: string }[]; location?: PickedLocation | null };
  submitLabel: string;
  onSubmit: (value: PostFormValue) => Promise<void>;
}) {
  const toast = useToast();
  const fileRef = useRef<HTMLInputElement>(null);
  const [content, setContent] = useState(initial?.content ?? "");
  const [postedDate, setPostedDate] = useState(initial?.postedDate ?? todayKst());
  const [slots, setSlots] = useState<Slot[]>(
    initial?.images.map((i) => ({ key: i.imageKey, preview: i.url, imageKey: i.imageKey })) ?? [],
  );
  const [location, setLocation] = useState<PickedLocation | null>(initial?.location ?? null);
  const [saving, setSaving] = useState(false);
  const [progress, setProgress] = useState("");

  // 떠날 때 미리보기 URL 해제 (최신 slots 를 ref 로)
  const slotsRef = useRef(slots);
  slotsRef.current = slots;
  useEffect(() => () => slotsRef.current.forEach((s) => s.file && URL.revokeObjectURL(s.preview)), []);

  const addFiles = (files: FileList | null) => {
    if (!files) return;
    const room = MAX_IMAGES - slots.length;
    const picked = Array.from(files).filter((f) => f.type.startsWith("image/") || /\.(heic|heif)$/i.test(f.name));
    if (picked.length > room) toast(`사진은 ${MAX_IMAGES}장까지 올릴 수 있어요`);
    setSlots((cur) => [
      ...cur,
      ...picked.slice(0, room).map((file) => ({ key: `${Date.now()}-${Math.random()}`, preview: URL.createObjectURL(file), file })),
    ]);
    if (fileRef.current) fileRef.current.value = "";
  };

  const removeSlot = (key: string) =>
    setSlots((cur) => {
      const target = cur.find((s) => s.key === key);
      if (target?.file) URL.revokeObjectURL(target.preview);
      return cur.filter((s) => s.key !== key);
    });

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!content.trim()) return;
    setSaving(true);
    try {
      const keys: string[] = [];
      for (let i = 0; i < slots.length; i++) {
        const s = slots[i];
        if (s.imageKey) {
          keys.push(s.imageKey);
        } else if (s.file) {
          setProgress(`사진 올리는 중 ${i + 1}/${slots.length}`);
          const imageKey = await uploadImage(s.file);
          s.imageKey = imageKey;
          keys.push(imageKey);
        }
      }
      setProgress("");
      await onSubmit({ content: content.trim(), postedDate, imageKeys: keys, location });
    } catch (err) {
      toast(err instanceof Error && !("code" in err) ? err.message : errorMessage(err));
      setSaving(false);
      setProgress("");
    }
  };

  const lines = content.split("\n").length;

  return (
    // 모바일: 본문 칸이 남는 높이를 채우고, 키보드가 열리면 사진·날짜 줄을 잠시 숨겨 버튼이 키보드 위에 보이게 함
    <form onSubmit={submit} className="flex flex-col gap-4 max-md:min-h-0 max-md:flex-1 group-data-[keyboard=open]:gap-3">
      <div className="flex flex-col rounded-3xl bg-white p-4 shadow-sm max-md:min-h-0 max-md:flex-1">
        <textarea
          className="min-h-[160px] w-full flex-1 resize-none max-md:min-h-[72px] bg-transparent text-[16px] leading-relaxed outline-none placeholder:text-sub"
          value={content}
          onChange={(e) => setContent(e.target.value.slice(0, MAX_LEN))}
          placeholder={"오늘 우리에게 있었던 일,\n세 줄이면 충분해요."}
          autoFocus={!initial}
          aria-label="내용"
        />
        <div className="flex items-center justify-between border-t border-line pt-3 text-xs text-sub">
          <span>{lines > 3 ? "세 줄이 넘어도 괜찮아요" : "세 줄 일기"}</span>
          <span className={content.length >= MAX_LEN ? "font-semibold text-brand-strong" : ""}>
            {content.length}/{MAX_LEN}
          </span>
        </div>
      </div>

      <div className="flex flex-wrap gap-2 group-data-[keyboard=open]:hidden">
        {slots.map((s) => (
          <div key={s.key} className="relative h-20 w-20 overflow-hidden rounded-2xl bg-line">
            <img src={s.preview} alt="" className="h-full w-full object-cover" />
            <button
              type="button"
              onClick={() => removeSlot(s.key)}
              className="absolute right-1 top-1 rounded-full bg-black/55 p-1 text-white"
              aria-label="사진 빼기"
            >
              <CloseIcon className="h-3.5 w-3.5" />
            </button>
          </div>
        ))}
        {slots.length < MAX_IMAGES && (
          <button
            type="button"
            onClick={() => fileRef.current?.click()}
            className="flex h-20 w-20 flex-col items-center justify-center gap-1 rounded-2xl border-2 border-dashed border-line text-xs text-sub hover:border-brand/40"
          >
            <ImageIcon className="h-6 w-6" />
            {slots.length}/{MAX_IMAGES}
          </button>
        )}
        <input ref={fileRef} type="file" accept="image/*" multiple hidden onChange={(e) => addFiles(e.target.files)} />
      </div>

      <div className="flex items-center justify-between gap-3 rounded-2xl bg-white py-2 pl-4 pr-3 shadow-sm group-data-[keyboard=open]:hidden">
        <span className="shrink-0 whitespace-nowrap text-sm font-medium text-sub">날짜</span>
        <div className="min-w-0 flex-1">
          <DateField label="기록 날짜" variant="inline" value={postedDate} max={todayKst()} onChange={setPostedDate} />
        </div>
      </div>

      <div className="group-data-[keyboard=open]:hidden">
        <LocationPicker value={location} onChange={setLocation} />
      </div>

      <Button type="submit" full loading={saving} disabled={!content.trim()}>
        {submitLabel}
      </Button>
      {progress && <p className="text-center text-sm text-sub">{progress}</p>}
    </form>
  );
}
