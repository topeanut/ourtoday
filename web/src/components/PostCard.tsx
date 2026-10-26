"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { api, errorMessage } from "@/lib/api";
import { formatDay } from "@/lib/date";
import { haptic, share } from "@/lib/native";
import type { EmojiType, Post } from "@/lib/types";
import { EditIcon, MoreIcon, ShareIcon, TrashIcon } from "./Icons";
import { useSession } from "./Session";
import { useToast } from "./Toast";
import { Avatar, Confirm, Sheet } from "./ui";

export const EMOJIS: { type: EmojiType; emoji: string; label: string }[] = [
  { type: "HEART", emoji: "❤️", label: "좋아" },
  { type: "LAUGH", emoji: "😂", label: "웃겨" },
  { type: "SURPRISE", emoji: "😮", label: "놀라워" },
  { type: "SAD", emoji: "😢", label: "슬퍼" },
  { type: "FIRE", emoji: "🔥", label: "최고" },
];

export function PostCard({
  post,
  onChange,
  onDeleted,
  linkToDetail = true,
}: {
  post: Post;
  onChange: (post: Post) => void;
  onDeleted?: () => void;
  linkToDetail?: boolean;
}) {
  const { me } = useSession();
  const router = useRouter();
  const toast = useToast();
  const [menu, setMenu] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [viewer, setViewer] = useState<number | null>(null);
  const mine = post.author.id === me.id;

  const onShare = async () => {
    const result = await share({ title: "오늘의 우리", text: post.content.slice(0, 40), url: `${location.origin}/posts/${post.id}` });
    if (result === "copied") toast("링크를 복사했어요");
  };

  const remove = async () => {
    setDeleting(true);
    try {
      await api(`/posts/${post.id}`, { method: "DELETE" });
      toast("기록을 지웠어요");
      setConfirmDelete(false);
      onDeleted?.();
    } catch (e) {
      toast(errorMessage(e));
    } finally {
      setDeleting(false);
    }
  };

  const body = <p className="whitespace-pre-line text-[15px] leading-relaxed">{post.content}</p>;

  return (
    <article className="rounded-3xl bg-white p-5 shadow-[0_1px_2px_rgba(43,35,38,0.04),0_8px_24px_rgba(43,35,38,0.04)]">
      <header className="mb-3 flex items-center gap-3">
        <Avatar name={post.author.nickname} seed={post.author.id} />
        <div className="min-w-0 flex-1">
          <p className="text-sm font-semibold">{post.author.nickname}</p>
          <p className="text-xs text-sub">{formatDay(post.postedDate)}</p>
        </div>
        <button onClick={onShare} className="rounded-full p-2 text-sub hover:bg-black/5" aria-label="공유">
          <ShareIcon className="h-5 w-5" />
        </button>
        {mine && (
          <button onClick={() => setMenu(true)} className="-mr-2 rounded-full p-2 text-sub hover:bg-black/5" aria-label="더보기">
            <MoreIcon className="h-5 w-5" />
          </button>
        )}
      </header>

      {linkToDetail ? <Link href={`/posts/${post.id}`}>{body}</Link> : body}

      {post.images.length > 0 && <ImageGrid images={post.images.map((i) => i.url)} onOpen={setViewer} />}

      <ReactionBar post={post} onChange={onChange} />

      <Sheet open={menu} onClose={() => setMenu(false)}>
        <div className="flex flex-col">
          <button
            className="flex items-center gap-3 rounded-2xl px-3 py-4 text-left font-medium hover:bg-black/5"
            onClick={() => router.push(`/posts/${post.id}/edit`)}
          >
            <EditIcon className="h-5 w-5" /> 수정하기
          </button>
          <button
            className="flex items-center gap-3 rounded-2xl px-3 py-4 text-left font-medium text-[#d6404e] hover:bg-black/5"
            onClick={() => {
              setMenu(false);
              setConfirmDelete(true);
            }}
          >
            <TrashIcon className="h-5 w-5" /> 삭제하기
          </button>
        </div>
      </Sheet>
      <Confirm
        open={confirmDelete}
        title="이 기록을 지울까요?"
        desc="사진과 반응도 함께 사라지고 되돌릴 수 없어요."
        confirmText="삭제"
        danger
        loading={deleting}
        onConfirm={remove}
        onClose={() => setConfirmDelete(false)}
      />
      {viewer !== null && <ImageViewer images={post.images.map((i) => i.url)} index={viewer} onClose={() => setViewer(null)} />}
    </article>
  );
}

function ImageGrid({ images, onOpen }: { images: string[]; onOpen: (i: number) => void }) {
  const layout =
    images.length === 1 ? "grid-cols-1" : images.length === 3 ? "grid-cols-3" : "grid-cols-2";
  return (
    <div className={`mt-3 grid gap-1.5 overflow-hidden rounded-2xl ${layout}`}>
      {images.map((src, i) => (
        <button key={src} onClick={() => onOpen(i)} className="relative block bg-line/50" aria-label={`사진 ${i + 1} 크게 보기`}>
          <img
            src={src}
            alt=""
            loading="lazy"
            className={`w-full object-cover ${images.length === 1 ? "max-h-[420px]" : "aspect-square"}`}
          />
        </button>
      ))}
    </div>
  );
}

function ImageViewer({ images, index, onClose }: { images: string[]; index: number; onClose: () => void }) {
  const [i, setI] = useState(index);
  return (
    <div className="animate-fade fixed inset-0 z-[70] flex items-center justify-center bg-black/90" onClick={onClose} role="dialog" aria-modal>
      <img src={images[i]} alt="" className="max-h-full max-w-full object-contain" />
      {images.length > 1 && (
        <div className="pb-safe absolute inset-x-0 bottom-6 flex justify-center gap-2" onClick={(e) => e.stopPropagation()}>
          {images.map((_, n) => (
            <button key={n} onClick={() => setI(n)} className={`h-2 w-2 rounded-full ${n === i ? "bg-white" : "bg-white/40"}`} aria-label={`${n + 1}번째 사진`} />
          ))}
        </div>
      )}
    </div>
  );
}

export function ReactionBar({ post, onChange }: { post: Post; onChange: (p: Post) => void }) {
  const toast = useToast();
  const [pending, setPending] = useState<EmojiType | null>(null);

  const toggle = async (type: EmojiType) => {
    if (pending) return;
    haptic();
    setPending(type);
    // 먼저 화면에 반영하고 실패하면 되돌림
    const before = post;
    onChange(applyToggle(post, type));
    try {
      await api(`/posts/${post.id}/reactions`, { method: "POST", body: { emojiType: type } });
    } catch (e) {
      onChange(before);
      toast(errorMessage(e));
    } finally {
      setPending(null);
    }
  };

  return (
    <div className="mt-4 flex flex-wrap gap-1.5">
      {EMOJIS.map(({ type, emoji, label }) => {
        const r = post.reactions.find((x) => x.emojiType === type);
        const on = r?.reactedByMe;
        return (
          <button
            key={type}
            onClick={() => toggle(type)}
            aria-pressed={!!on}
            aria-label={`${label} ${r?.count ?? 0}`}
            className={`flex h-9 items-center gap-1 rounded-full px-3 text-sm transition active:scale-95 ${
              on ? "bg-brand-soft font-semibold text-brand-strong ring-1 ring-brand/30" : r ? "bg-cream" : "bg-cream/70 grayscale-[35%]"
            }`}
          >
            <span>{emoji}</span>
            {r && <span>{r.count}</span>}
          </button>
        );
      })}
    </div>
  );
}

function applyToggle(post: Post, type: EmojiType): Post {
  const existing = post.reactions.find((r) => r.emojiType === type);
  let reactions;
  if (!existing) {
    reactions = [...post.reactions, { emojiType: type, count: 1, reactedByMe: true }];
  } else if (existing.reactedByMe) {
    reactions = post.reactions
      .map((r) => (r.emojiType === type ? { ...r, count: r.count - 1, reactedByMe: false } : r))
      .filter((r) => r.count > 0);
  } else {
    reactions = post.reactions.map((r) => (r.emojiType === type ? { ...r, count: r.count + 1, reactedByMe: true } : r));
  }
  const order = EMOJIS.map((e) => e.type);
  reactions.sort((a, b) => order.indexOf(a.emojiType) - order.indexOf(b.emojiType));
  return { ...post, reactions };
}
