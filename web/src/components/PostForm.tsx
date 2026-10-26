"use client";

import { useEffect, useRef, useState } from "react";
import { errorMessage } from "@/lib/api";
import { todayKst } from "@/lib/date";
import { uploadImage } from "@/lib/image";
import { CloseIcon, ImageIcon } from "./Icons";
import { useToast } from "./Toast";
import { Button, inputClass } from "./ui";

const MAX_LEN = 300;
const MAX_IMAGES = 4;

type Slot = { key: string; preview: string; imageKey?: string; file?: File };

export interface PostFormValue {
  content: string;
  postedDate: string;
  imageKeys: string[];
}

export function PostForm({
  initial,
  submitLabel,
  onSubmit,
}: {
  initial?: { content: string; postedDate: string; images: { url: string; imageKey: string }[] };
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
  const [saving, setSaving] = useState(false);
  const [progress, setProgress] = useState("");

  useEffect(() => {
    return () => slots.forEach((s) => s.file && URL.revokeObjectURL(s.preview));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const addFiles = (files: FileList | null) => {
    if (!files) return;
    const room = MAX_IMAGES - slots.length;
    const picked = Array.from(files).filter((f) => f.type.startsWith("image/") || /\.(heic|heif)$/i.test(f.name));
    if (picked.length > room) toast(`사진은 ${MAX_IMAGES}장까지 올릴 수 있어요`);
    setSlots((cur) => [
      ...cur,
      ...picked.slice(0, room).map((file) => ({ key: crypto.randomUUID(), preview: URL.createObjectURL(file), file })),
    ]);
    if (fileRef.current) fileRef.current.value = "";
  };

  const removeSlot = (key: string) => setSlots((cur) => cur.filter((s) => s.key !== key));

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
      await onSubmit({ content: content.trim(), postedDate, imageKeys: keys });
    } catch (err) {
      toast(err instanceof Error && !("code" in err) ? err.message : errorMessage(err));
      setSaving(false);
      setProgress("");
    }
  };

  const lines = content.split("\n").length;

  return (
    <form onSubmit={submit} className="flex flex-col gap-4">
      <div className="rounded-3xl bg-white p-4 shadow-sm">
        <textarea
          className="min-h-[160px] w-full resize-none bg-transparent text-[16px] leading-relaxed outline-none placeholder:text-sub/60"
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

      <div className="flex flex-wrap gap-2">
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

      <label className="flex items-center justify-between rounded-2xl bg-white px-4 py-2 shadow-sm">
        <span className="shrink-0 whitespace-nowrap text-sm font-medium text-sub">날짜</span>
        <input
          type="date"
          className={`${inputClass} w-auto border-0 bg-transparent px-0 py-2 text-right focus:ring-0`}
          value={postedDate}
          max={todayKst()}
          onChange={(e) => e.target.value && setPostedDate(e.target.value)}
        />
      </label>

      <Button type="submit" full loading={saving} disabled={!content.trim()}>
        {submitLabel}
      </Button>
      {progress && <p className="text-center text-sm text-sub">{progress}</p>}
    </form>
  );
}
