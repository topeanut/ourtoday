"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import useSWR from "swr";
import { api, errorMessage, fetcher } from "@/lib/api";
import { formatDay, parseDate, todayKst, toYmd, weekdayLabels } from "@/lib/date";
import type { CalendarMemo, Page, Post } from "@/lib/types";
import { PageHeader } from "@/components/AppShell";
import { ChevronLeft, ChevronRight, EditIcon, MapPin, PlusIcon } from "@/components/Icons";
import { LocationPicker } from "@/components/LocationPicker";
import type { PickedLocation } from "@/lib/kakaoMap";
import { useSession } from "@/components/Session";
import { useToast } from "@/components/Toast";
import { DayItemsSkeleton } from "@/components/Skeletons";
import { Button, Confirm, DateField, Field, IconButton, inputClass, PRESS, Pressable, Sheet, SheetActions } from "@/components/ui";

const COLORS = ["#FF7A8A", "#FFA36C", "#F6C445", "#7CC69A", "#6FB3E8", "#A68EE8"];

/** 월간 그리드(일요일 시작, 6주)의 첫날·마지막날 */
function gridRange(year: number, month: number) {
  const first = new Date(year, month, 1);
  const start = new Date(first);
  start.setDate(1 - first.getDay());
  const days = Array.from({ length: 42 }, (_, i) => {
    const d = new Date(start);
    d.setDate(start.getDate() + i);
    return toYmd(d);
  });
  return { days, from: days[0], to: days[41] };
}

/** 그 달 게시글 전부 (50개씩 이어서) */
async function fetchMonthPosts(from: string, to: string): Promise<Post[]> {
  const all: Post[] = [];
  for (let page = 0; page < 10; page++) {
    const res = await api<Page<Post>>(`/posts?from=${from}&to=${to}&size=50&page=${page}&sort=postedDate,asc`);
    all.push(...res.content);
    if (page + 1 >= res.page.totalPages) break;
  }
  return all;
}

export default function CalendarPage() {
  const today = todayKst();
  const [cursor, setCursor] = useState(() => {
    const d = parseDate(today);
    return { year: d.getFullYear(), month: d.getMonth() };
  });
  const [selected, setSelected] = useState(today);
  const [editing, setEditing] = useState<CalendarMemo | "new" | null>(null);

  const { days, from, to } = useMemo(() => gridRange(cursor.year, cursor.month), [cursor]);
  const memos = useSWR<{ items: CalendarMemo[] }>(`/calendar/memos?from=${from}&to=${to}`, fetcher);
  const posts = useSWR(["month-posts", from, to], () => fetchMonthPosts(from, to));

  const memosByDay = useMemo(() => group(memos.data?.items ?? [], (m) => m.date), [memos.data]);
  const postsByDay = useMemo(() => group(posts.data ?? [], (p) => p.postedDate), [posts.data]);

  const move = (delta: number) => {
    setCursor(({ year, month }) => {
      const d = new Date(year, month + delta, 1);
      return { year: d.getFullYear(), month: d.getMonth() };
    });
  };

  const selectedMemos = memosByDay.get(selected) ?? [];
  const selectedPosts = postsByDay.get(selected) ?? [];

  return (
    <>
      <PageHeader
        title={`${cursor.year}년 ${cursor.month + 1}월`}
        right={
          <div className="flex items-center gap-1">
            <Pressable
              onClick={() => {
                const d = parseDate(today);
                setCursor({ year: d.getFullYear(), month: d.getMonth() });
                setSelected(today);
              }}
              className="mr-1 h-9 rounded-full bg-white px-3 text-sm font-medium text-sub active:bg-black/5"
            >
              오늘
            </Pressable>
            <IconButton onClick={() => move(-1)} label="이전 달">
              <ChevronLeft className="h-5 w-5" />
            </IconButton>
            <IconButton onClick={() => move(1)} label="다음 달" className="-mr-2.5">
              <ChevronRight className="h-5 w-5" />
            </IconButton>
          </div>
        }
      />
      <div className="mx-auto max-w-2xl px-4 pb-10 md:px-8">
        <div className="rounded-3xl bg-white p-3 shadow-sm">
          <div className="grid grid-cols-7 pb-1 text-center text-xs font-medium text-sub">
            {weekdayLabels.map((w, i) => (
              <span key={w} className={i === 0 ? "text-[#e0566a]" : i === 6 ? "text-[#4f86c6]" : ""}>
                {w}
              </span>
            ))}
          </div>
          <div className="grid grid-cols-7 gap-y-1">
            {days.map((day) => {
              const d = parseDate(day);
              const inMonth = d.getMonth() === cursor.month;
              const dayMemos = memosByDay.get(day) ?? [];
              const hasPost = postsByDay.has(day);
              const isSel = day === selected;
              return (
                <Pressable
                  key={day}
                  onClick={() => setSelected(day)}
                  className={`flex aspect-square flex-col items-center justify-start rounded-2xl pt-1.5 transition md:aspect-[1.2] ${
                    isSel ? "bg-brand-soft" : "hover:bg-cream active:bg-cream"
                  } ${inMonth ? "" : "opacity-35"}`}
                  aria-label={`${formatDay(day)}${dayMemos.length ? `, 메모 ${dayMemos.length}개` : ""}${hasPost ? ", 기록 있음" : ""}`}
                  aria-pressed={isSel}
                >
                  <span
                    className={`flex h-7 w-7 items-center justify-center rounded-full text-sm ${
                      day === today ? "bg-brand font-bold text-white" : isSel ? "font-semibold text-brand-strong" : ""
                    }`}
                  >
                    {d.getDate()}
                  </span>
                  <span className="mt-1 flex h-1.5 gap-0.5">
                    {hasPost && <span className="h-1.5 w-1.5 rounded-full bg-ink/70" />}
                    {dayMemos.slice(0, 3).map((m) => (
                      <span key={m.id} className="h-1.5 w-1.5 rounded-full" style={{ background: m.color ?? "#FF7A8A" }} />
                    ))}
                  </span>
                </Pressable>
              );
            })}
          </div>
          <div className="mt-2 flex justify-end gap-3 px-2 text-[11px] text-sub">
            <span className="flex items-center gap-1">
              <span className="h-1.5 w-1.5 rounded-full bg-ink/70" /> 기록
            </span>
            <span className="flex items-center gap-1">
              {/* 메모 점은 메모마다 고른 색이라 대표 색 몇 개를 같이 보여 줌 */}
              <span className="flex -space-x-0.5">
                {COLORS.slice(0, 3).map((c) => (
                  <span key={c} className="h-1.5 w-1.5 rounded-full ring-1 ring-white" style={{ background: c }} />
                ))}
              </span>{" "}
              메모·일정
            </span>
          </div>
        </div>

        <section className="mt-5">
          <div className="mb-3 flex items-center justify-between">
            <h2 className="font-bold">{formatDay(selected)}</h2>
            <Pressable onClick={() => setEditing("new")} className="flex h-9 items-center gap-1 rounded-full bg-brand px-3 text-sm font-semibold text-white active:bg-brand-strong">
              <PlusIcon className="h-4 w-4" /> 메모
            </Pressable>
          </div>
          {(memos.isLoading || posts.isLoading) && <DayItemsSkeleton />}
          {!memos.isLoading && !posts.isLoading && selectedMemos.length === 0 && selectedPosts.length === 0 && (
            <p className="rounded-3xl bg-white/60 px-5 py-8 text-center text-sm text-sub">이날은 비어 있어요. 일정이나 메모를 남겨 보세요.</p>
          )}
          <ul className="animate-fade flex flex-col gap-2">
            {selectedMemos.map((m) => (
              <li key={m.id}>
                <Pressable press="soft" onClick={() => setEditing(m)} className="flex w-full items-start gap-3 rounded-3xl bg-white p-4 text-left shadow-sm">
                  <span className="mt-1.5 h-3 w-3 shrink-0 rounded-full" style={{ background: m.color ?? "#FF7A8A" }} />
                  <span className="min-w-0 flex-1">
                    <span className="block font-medium">{m.title}</span>
                    {m.description && <span className="mt-0.5 block whitespace-pre-line text-sm text-sub">{m.description}</span>}
                    {m.location && (
                      <span className="mt-1 flex items-center gap-1 text-xs text-sub">
                        <MapPin className="h-3.5 w-3.5 shrink-0" />
                        <span className="truncate">{m.location.placeName}</span>
                      </span>
                    )}
                    <span className="mt-1 block text-xs text-sub">{m.author.nickname}</span>
                  </span>
                </Pressable>
              </li>
            ))}
            {selectedPosts.map((p) => (
              <li key={p.id}>
                <Link href={`/posts/${p.id}`} className={`flex items-start gap-3 rounded-3xl bg-white p-4 shadow-sm ${PRESS.soft}`}>
                  {p.images[0] ? (
                    <img src={p.images[0].url} alt="" className="h-12 w-12 shrink-0 rounded-xl object-cover" />
                  ) : (
                    <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-brand-soft text-brand-strong">
                      <EditIcon className="h-5 w-5" />
                    </span>
                  )}
                  <span className="min-w-0 flex-1">
                    <span className="block text-xs text-sub">{p.author.nickname}의 기록</span>
                    <span className="mt-0.5 line-clamp-2 text-[15px]">{p.content}</span>
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        </section>
      </div>

      <MemoEditor
        target={editing}
        date={selected}
        onClose={() => setEditing(null)}
        onSaved={(date) => {
          setEditing(null);
          if (date) {
            setSelected(date);
            const d = parseDate(date);
            setCursor({ year: d.getFullYear(), month: d.getMonth() });
          }
          memos.mutate();
        }}
      />
    </>
  );
}

function group<T>(items: T[], key: (t: T) => string) {
  const map = new Map<string, T[]>();
  for (const item of items) {
    const k = key(item);
    map.set(k, [...(map.get(k) ?? []), item]);
  }
  return map;
}

function MemoEditor({
  target,
  date,
  onClose,
  onSaved,
}: {
  target: CalendarMemo | "new" | null;
  date: string;
  onClose: () => void;
  onSaved: (date?: string) => void;
}) {
  const { me } = useSession();
  const toast = useToast();
  const memo = target && target !== "new" ? target : null;
  const readOnly = !!memo && memo.author.id !== me.id;
  const [form, setForm] = useState({ date, title: "", description: "", color: COLORS[0] });
  const [location, setLocation] = useState<PickedLocation | null>(null);
  const [loadedFor, setLoadedFor] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);

  const key = target === "new" ? `new-${date}` : memo?.id ?? null;
  if (key !== loadedFor) {
    setLoadedFor(key);
    setForm({
      date: memo?.date ?? date,
      title: memo?.title ?? "",
      description: memo?.description ?? "",
      color: memo?.color ?? COLORS[0],
    });
    setLocation(memo?.location ?? null);
  }

  const save = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    const body = { date: form.date, title: form.title.trim(), description: form.description.trim() || null, color: form.color, location };
    try {
      if (memo) await api(`/calendar/memos/${memo.id}`, { method: "PATCH", body });
      else await api("/calendar/memos", { method: "POST", body });
      onSaved(form.date);
    } catch (err) {
      toast(errorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  const remove = async () => {
    if (!memo) return;
    setSaving(true);
    try {
      await api(`/calendar/memos/${memo.id}`, { method: "DELETE" });
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
      <Sheet open={!!target && !confirmDelete} onClose={onClose} title={readOnly ? memo!.title : memo ? "메모 수정" : "메모·일정 추가"}>
        {readOnly ? (
          <div className="text-[15px]">
            <p className="text-sm text-sub">{formatDay(memo!.date)} · {memo!.author.nickname}</p>
            {memo!.description && <p className="mt-3 whitespace-pre-line">{memo!.description}</p>}
            {memo!.location && (
              <p className="mt-3 flex items-center gap-1.5 text-sm text-sub">
                <MapPin className="h-4 w-4 shrink-0" />
                <span className="truncate">{memo!.location.placeName}</span>
              </p>
            )}
            <p className="mt-6 text-xs text-sub">상대가 쓴 메모는 상대만 고칠 수 있어요.</p>
          </div>
        ) : (
          <form onSubmit={save} className="flex flex-col gap-4">
            <Field label="제목" hint={`${form.title.length}/50`}>
              <input
                className={inputClass}
                value={form.title}
                onChange={(e) => setForm({ ...form, title: e.target.value.slice(0, 50) })}
                placeholder="예) 1주년 기념일"
                autoFocus
              />
            </Field>
            <Field label="날짜">
              <DateField label="날짜" value={form.date} onChange={(v) => setForm({ ...form, date: v })} />
            </Field>
            <Field label="내용 (선택)">
              <textarea
                className={`${inputClass} min-h-[80px] resize-none`}
                value={form.description}
                onChange={(e) => setForm({ ...form, description: e.target.value.slice(0, 1000) })}
                placeholder="장소, 시간, 준비할 것"
              />
            </Field>
            <LocationPicker value={location} onChange={setLocation} />
            <div>
              <p className="mb-2 text-sm font-medium text-sub">색상</p>
              <div className="flex gap-3">
                {COLORS.map((c) => (
                  <Pressable
                    key={c}
                    onClick={() => setForm({ ...form, color: c })}
                    className={`h-8 w-8 rounded-full ${form.color === c ? "ring-2 ring-ink ring-offset-2" : ""}`}
                    style={{ background: c }}
                    aria-label={`색상 ${c}`}
                    aria-pressed={form.color === c}
                  />
                ))}
              </div>
            </div>
            <SheetActions>
              <Button type="submit" full loading={saving} disabled={!form.title.trim()}>
                {memo ? "저장" : "추가"}
              </Button>
            </SheetActions>
            {memo && (
              <Button type="button" variant="danger" full onClick={() => setConfirmDelete(true)}>
                삭제
              </Button>
            )}
          </form>
        )}
      </Sheet>
      <Confirm
        open={confirmDelete}
        title="이 메모를 지울까요?"
        confirmText="삭제"
        danger
        loading={saving}
        onConfirm={remove}
        onClose={() => setConfirmDelete(false)}
      />
    </>
  );
}

