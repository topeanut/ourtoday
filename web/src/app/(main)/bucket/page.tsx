"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import useSWR from "swr";
import { api, errorMessage, fetcher } from "@/lib/api";
import { formatDay, relativeTime } from "@/lib/date";
import { haptic } from "@/lib/native";
import type { BucketItem } from "@/lib/types";
import { PageHeader } from "@/components/AppShell";
import { CheckIcon, PlusIcon } from "@/components/Icons";
import { useToast } from "@/components/Toast";
import { Button, Confirm, Empty, Field, inputClass, PageSpinner, Sheet, SheetActions } from "@/components/ui";

type Status = "ALL" | "TODO" | "DONE";

export default function BucketPage() {
  const router = useRouter();
  const toast = useToast();
  const [status, setStatus] = useState<Status>("ALL");
  const { data, mutate } = useSWR<{ items: BucketItem[] }>(`/bucket-items?status=${status}`, fetcher);
  const all = useSWR<{ items: BucketItem[] }>("/bucket-items?status=ALL", fetcher);
  const [editing, setEditing] = useState<BucketItem | "new" | null>(null);
  const [completing, setCompleting] = useState<BucketItem | null>(null);

  const done = all.data?.items.filter((i) => i.completed).length ?? 0;
  const total = all.data?.items.length ?? 0;

  const refresh = () => {
    mutate();
    all.mutate();
  };

  const uncomplete = async (item: BucketItem) => {
    try {
      await api(`/bucket-items/${item.id}/complete`, { method: "DELETE" });
      refresh();
    } catch (e) {
      toast(errorMessage(e));
    }
  };

  const completeOnly = async (item: BucketItem) => {
    try {
      haptic();
      await api(`/bucket-items/${item.id}/complete`, { method: "PATCH" });
      setCompleting(null);
      toast("달성 축하해요! 🎉");
      refresh();
    } catch (e) {
      toast(errorMessage(e));
    }
  };

  return (
    <>
      <PageHeader
        title="버킷리스트"
        right={
          <button onClick={() => setEditing("new")} className="rounded-full bg-brand p-2 text-white" aria-label="추가">
            <PlusIcon className="h-5 w-5" />
          </button>
        }
      />
      <div className="mx-auto max-w-2xl px-4 pb-10 md:px-8">
        {total > 0 && (
          <div className="mb-4 rounded-3xl bg-white p-5 shadow-sm">
            <div className="flex items-baseline justify-between">
              <p className="font-semibold">함께 이룬 것</p>
              <p className="text-sm text-sub">
                <b className="text-lg text-brand-strong">{done}</b> / {total}
              </p>
            </div>
            <div className="mt-3 h-2 overflow-hidden rounded-full bg-cream">
              <div className="h-full rounded-full bg-brand transition-all" style={{ width: `${(done / total) * 100}%` }} />
            </div>
          </div>
        )}

        <div className="mb-4 flex gap-2" role="tablist">
          {(
            [
              ["ALL", "전체"],
              ["TODO", "하고 싶은 것"],
              ["DONE", "이룬 것"],
            ] as [Status, string][]
          ).map(([key, label]) => (
            <button
              key={key}
              role="tab"
              aria-selected={status === key}
              onClick={() => setStatus(key)}
              className={`h-9 rounded-full px-4 text-sm font-medium ${status === key ? "bg-ink text-white" : "bg-white text-sub"}`}
            >
              {label}
            </button>
          ))}
        </div>

        {!data ? (
          <PageSpinner />
        ) : data.items.length === 0 ? (
          <Empty
            title={status === "DONE" ? "아직 이룬 게 없어요" : "함께 하고 싶은 걸 적어 보세요"}
            desc="여행, 맛집, 작은 도전까지 뭐든 좋아요"
            action={status !== "DONE" && <Button onClick={() => setEditing("new")}>추가하기</Button>}
          />
        ) : (
          <ul className="flex flex-col gap-2">
            {data.items.map((item) => (
              <li key={item.id} className="flex items-start gap-3 rounded-3xl bg-white p-4 shadow-sm">
                <button
                  onClick={() => (item.completed ? uncomplete(item) : setCompleting(item))}
                  className={`mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-full border-2 transition ${
                    item.completed ? "border-brand bg-brand text-white" : "border-line text-transparent hover:border-brand/50"
                  }`}
                  aria-label={item.completed ? "완료 취소" : "완료"}
                >
                  <CheckIcon className="h-4 w-4" />
                </button>
                <button className="min-w-0 flex-1 text-left" onClick={() => setEditing(item)}>
                  <p className={`font-medium ${item.completed ? "text-sub line-through decoration-brand/50" : ""}`}>{item.title}</p>
                  {item.description && <p className="mt-0.5 line-clamp-2 text-sm text-sub">{item.description}</p>}
                  <p className="mt-1 text-xs text-sub">
                    {item.completed && item.completedAt
                      ? `${relativeTime(item.completedAt)} 달성`
                      : item.targetDate
                        ? `목표 ${formatDay(item.targetDate)}`
                        : `${item.createdBy.nickname}님이 추가`}
                  </p>
                </button>
                {item.completedPostId && (
                  <Link href={`/posts/${item.completedPostId}`} className="shrink-0 rounded-full bg-brand-soft px-3 py-1.5 text-xs font-semibold text-brand-strong">
                    기록 보기
                  </Link>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>

      <BucketEditor
        target={editing}
        onClose={() => setEditing(null)}
        onSaved={() => {
          setEditing(null);
          refresh();
        }}
      />

      <Sheet open={!!completing} onClose={() => setCompleting(null)} title="달성했나요? 🎉">
        {completing && (
          <>
            <p className="mb-5 text-sub">
              <b className="text-ink">{completing.title}</b>
              <br />그날의 기록을 남기면 나중에 함께 다시 볼 수 있어요.
            </p>
            <div className="flex flex-col gap-2">
              <Button full onClick={() => router.push(`/posts/new?bucket=${completing.id}`)}>
                기록 남기고 완료하기
              </Button>
              <Button full variant="soft" onClick={() => completeOnly(completing)}>
                그냥 완료하기
              </Button>
            </div>
          </>
        )}
      </Sheet>
    </>
  );
}

function BucketEditor({
  target,
  onClose,
  onSaved,
}: {
  target: BucketItem | "new" | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const toast = useToast();
  const item = target && target !== "new" ? target : null;
  const [form, setForm] = useState({ title: "", description: "", targetDate: "" });
  const [loadedFor, setLoadedFor] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);

  const key = target === "new" ? "new" : item?.id ?? null;
  if (key !== loadedFor) {
    setLoadedFor(key);
    setForm({ title: item?.title ?? "", description: item?.description ?? "", targetDate: item?.targetDate ?? "" });
  }

  const save = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    const body = { title: form.title.trim(), description: form.description.trim() || null, targetDate: form.targetDate || null };
    try {
      if (item) await api(`/bucket-items/${item.id}`, { method: "PATCH", body });
      else await api("/bucket-items", { method: "POST", body });
      onSaved();
    } catch (err) {
      toast(errorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  const remove = async () => {
    if (!item) return;
    setSaving(true);
    try {
      await api(`/bucket-items/${item.id}`, { method: "DELETE" });
      setConfirmDelete(false);
      onSaved();
    } catch (err) {
      toast(errorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <>
      <Sheet open={!!target && !confirmDelete} onClose={onClose} title={item ? "버킷리스트 수정" : "버킷리스트 추가"}>
        <form onSubmit={save} className="flex flex-col gap-4">
          <Field label="하고 싶은 것" hint={`${form.title.length}/100`}>
            <input
              className={inputClass}
              value={form.title}
              onChange={(e) => setForm({ ...form, title: e.target.value.slice(0, 100) })}
              placeholder="예) 오로라 보러 가기"
              autoFocus
            />
          </Field>
          <Field label="메모 (선택)">
            <textarea
              className={`${inputClass} min-h-[80px] resize-none`}
              value={form.description}
              onChange={(e) => setForm({ ...form, description: e.target.value.slice(0, 1000) })}
              placeholder="어디서, 어떻게"
            />
          </Field>
          <Field label="목표 날짜 (선택)">
            <input type="date" className={inputClass} value={form.targetDate} onChange={(e) => setForm({ ...form, targetDate: e.target.value })} />
          </Field>
          <SheetActions>
            <Button type="submit" full loading={saving} disabled={!form.title.trim()}>
              {item ? "저장" : "추가"}
            </Button>
          </SheetActions>
          {item && (
            <Button type="button" variant="danger" full onClick={() => setConfirmDelete(true)}>
              삭제
            </Button>
          )}
        </form>
      </Sheet>
      <Confirm
        open={confirmDelete}
        title="이 항목을 지울까요?"
        desc="연결된 기록은 지워지지 않아요."
        confirmText="삭제"
        danger
        loading={saving}
        onConfirm={remove}
        onClose={() => setConfirmDelete(false)}
      />
    </>
  );
}
