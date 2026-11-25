"use client";

import { useEffect, useRef, useState } from "react";
import useSWR from "swr";
import { api, errorMessage, fetcher, revalidateAll } from "@/lib/api";
import { relativeTime } from "@/lib/date";
import { useKeyboardViewport } from "@/lib/useKeyboardViewport";
import type { Comment } from "@/lib/types";
import { EditIcon, MoreIcon, TrashIcon } from "./Icons";
import { useSession } from "./Session";
import { useToast } from "./Toast";
import { CommentsSkeleton } from "./Skeletons";
import { Avatar, Button, Confirm, IconButton, inputClass, MenuItem, Sheet } from "./ui";

const MAX = 200;
/** 서버 저장 전 임시 댓글 id 앞에 붙음 */
const PENDING_PREFIX = "pending:";
const COUNT_FROM = 180;

export function CommentSection({ postId }: { postId: string }) {
  const { me } = useSession();
  const toast = useToast();
  const { data, error, mutate } = useSWR<Comment[]>(`/posts/${postId}/comments`, fetcher);
  const [menuFor, setMenuFor] = useState<Comment | null>(null);
  const [editing, setEditing] = useState<Comment | null>(null);
  const [deleting, setDeleting] = useState<Comment | null>(null);
  const [busy, setBusy] = useState(false);

  // 목록·상세 게시글(댓글 수)·피드 미리보기를 한꺼번에 다시 불러옴
  const refresh = async () => {
    await mutate();
    revalidateAll();
  };

  /** 서버 응답을 기다리지 않고 바로 목록에 붙임. 실패하면 SWR 이 원래 목록으로 되돌림 */
  const create = async (content: string) => {
    const now = new Date().toISOString();
    const author = { id: me.id, nickname: me.nickname, profileImageUrl: me.profileImageUrl };
    const temp: Comment = { id: `${PENDING_PREFIX}${now}`, author, content, createdAt: now, updatedAt: now };
    await mutate(
      async (cur) => {
        const saved = await api<Comment>(`/posts/${postId}/comments`, { method: "POST", body: { content } });
        return [...(cur ?? []), saved];
      },
      { optimisticData: (cur) => [...(cur ?? []), temp], rollbackOnError: true, revalidate: false },
    );
    revalidateAll();
  };

  /** 시트를 바로 닫고 목록에 먼저 반영, 실패하면 되돌리고 토스트 */
  const saveEdit = async (content: string) => {
    if (!editing) return;
    const target = editing;
    setEditing(null);
    try {
      await mutate(
        async (cur) => {
          const saved = await api<Comment>(`/posts/${postId}/comments/${target.id}`, { method: "PATCH", body: { content } });
          return (cur ?? []).map((c) => (c.id === saved.id ? saved : c));
        },
        {
          optimisticData: (cur) => (cur ?? []).map((c) => (c.id === target.id ? { ...c, content } : c)),
          rollbackOnError: true,
          revalidate: false,
        },
      );
      revalidateAll();
    } catch (e) {
      toast(errorMessage(e));
    }
  };

  const remove = async () => {
    if (!deleting) return;
    setBusy(true);
    try {
      await api(`/posts/${postId}/comments/${deleting.id}`, { method: "DELETE" });
      setDeleting(null);
      toast("댓글을 지웠어요");
      await refresh();
    } catch (e) {
      toast(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="mt-6" aria-label="댓글">
      <h2 className="mb-1 px-1 text-sm font-semibold">
        댓글 {data && data.length > 0 && <span className="text-brand-strong">{data.length}</span>}
      </h2>

      {error && !data ? (
        <p className="py-8 text-center text-sm text-sub">댓글을 불러오지 못했어요</p>
      ) : !data ? (
        <CommentsSkeleton />
      ) : data.length === 0 ? (
        <p className="py-8 text-center text-sm text-sub">아직 댓글이 없어요</p>
      ) : (
        <ul className="divide-y divide-line">
          {data.map((c) => {
            const pending = c.id.startsWith(PENDING_PREFIX);
            return (
              <li key={c.id} className={`flex gap-3 px-1 py-3.5 ${pending ? "animate-fade opacity-60" : ""}`} aria-busy={pending || undefined}>
                <Avatar name={c.author.nickname} seed={c.author.id} src={c.author.profileImageUrl} size={32} />
                <div className="min-w-0 flex-1">
                  <p className="flex items-baseline gap-2">
                    <span className="text-sm font-semibold">{c.author.nickname}</span>
                    <span className="text-xs text-sub">{relativeTime(c.createdAt)}</span>
                  </p>
                  <p className="mt-0.5 whitespace-pre-line break-words text-[15px] leading-relaxed">{c.content}</p>
                </div>
                {c.author.id === me.id && !pending && (
                  <IconButton onClick={() => setMenuFor(c)} tone="sub" label="댓글 더보기" className="-mr-2.5 -mt-2">
                    <MoreIcon className="h-5 w-5" />
                  </IconButton>
                )}
              </li>
            );
          })}
        </ul>
      )}

      {/* 모바일은 화면 하단 고정 입력창이 마지막 댓글을 가리지 않게 여백 */}
      <div className="h-28 md:h-4" aria-hidden />
      <Composer onSubmit={create} />

      <Sheet open={!!menuFor} onClose={() => setMenuFor(null)}>
        <div className="flex flex-col">
          <MenuItem
            icon={<EditIcon className="h-5 w-5" />}
            onClick={() => {
              setEditing(menuFor);
              setMenuFor(null);
            }}
          >
            수정하기
          </MenuItem>
          <MenuItem
            danger
            icon={<TrashIcon className="h-5 w-5" />}
            onClick={() => {
              setDeleting(menuFor);
              setMenuFor(null);
            }}
          >
            삭제하기
          </MenuItem>
        </div>
      </Sheet>

      <Sheet open={!!editing} onClose={() => setEditing(null)} title="댓글 수정">
        {editing && <EditForm key={editing.id} initial={editing.content} onSubmit={saveEdit} />}
      </Sheet>

      <Confirm
        open={!!deleting}
        title="이 댓글을 지울까요?"
        desc="지운 댓글은 되돌릴 수 없어요."
        confirmText="삭제"
        danger
        loading={busy}
        onConfirm={remove}
        onClose={() => setDeleting(null)}
      />
    </section>
  );
}

function Counter({ length }: { length: number }) {
  if (length < COUNT_FROM) return null;
  return (
    <span className={`text-xs tabular-nums ${length > MAX ? "text-[#d6404e]" : "text-sub"}`}>
      {MAX - length}
    </span>
  );
}

/** 하단 고정 입력창. 모바일은 키보드 위에 붙고, PC 는 본문 아래에 달라붙음 */
function Composer({ onSubmit }: { onSubmit: (content: string) => Promise<void> }) {
  const toast = useToast();
  const wrapRef = useRef<HTMLDivElement>(null);
  const [mobile, setMobile] = useState(false);
  const [text, setText] = useState("");

  useEffect(() => {
    const mq = window.matchMedia("(max-width: 767px)");
    const sync = () => setMobile(mq.matches);
    sync();
    mq.addEventListener("change", sync);
    return () => mq.removeEventListener("change", sync);
  }, []);
  useKeyboardViewport(wrapRef, mobile);

  const valid = text.trim().length > 0 && text.length <= MAX;

  // 입력창은 바로 비우고, 실패하면 쓴 내용을 되돌려 줌 (그 사이 새로 쓴 게 없을 때만)
  const submit = async () => {
    if (!valid) return;
    const content = text.trim();
    setText("");
    try {
      await onSubmit(content);
    } catch (e) {
      toast(errorMessage(e));
      setText((cur) => cur || content);
    }
  };

  return (
    <div
      ref={wrapRef}
      data-keyboard="closed"
      className="group pointer-events-none max-md:fixed max-md:inset-x-0 max-md:top-0 max-md:z-40 max-md:flex max-md:h-dvh max-md:items-end md:sticky md:bottom-0"
    >
      <div className="pointer-events-auto w-full border-t border-line bg-white/95 px-4 pb-3 pt-3 backdrop-blur max-md:pb-[calc(12px+var(--safe-bottom))] max-md:group-data-[keyboard=open]:pb-3 md:rounded-2xl md:border">
        <div className="mx-auto flex max-w-2xl items-end gap-2">
          <textarea
            value={text}
            onChange={(e) => setText(e.target.value)}
            rows={1}
            placeholder="댓글을 남겨 보세요"
            aria-label="댓글 입력"
            className={`${inputClass} max-h-28 min-h-11 flex-1 resize-none py-2.5 text-[15px]`}
          />
          <div className="flex flex-col items-end gap-1">
            <Counter length={text.length} />
            <Button onClick={submit} disabled={!valid} size="md" className="shrink-0">
              등록
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}

function EditForm({ initial, onSubmit }: { initial: string; onSubmit: (content: string) => Promise<void> }) {
  const [text, setText] = useState(initial);
  const valid = text.trim().length > 0 && text.length <= MAX && text.trim() !== initial.trim();

  const submit = () => {
    if (valid) void onSubmit(text.trim());
  };

  return (
    <div className="flex flex-col gap-2">
      <textarea
        value={text}
        onChange={(e) => setText(e.target.value)}
        rows={4}
        autoFocus
        aria-label="댓글 수정"
        className={`${inputClass} resize-none text-[15px]`}
      />
      <div className="flex items-center justify-between">
        <Counter length={text.length} />
        <Button onClick={submit} disabled={!valid} size="md" className="ml-auto">
          저장
        </Button>
      </div>
    </div>
  );
}
