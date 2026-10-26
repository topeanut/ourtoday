"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import useSWR from "swr";
import useSWRInfinite from "swr/infinite";
import { fetcher } from "@/lib/api";
import { formatDay } from "@/lib/date";
import type { Memories, Page, Post } from "@/lib/types";
import { PageHeader } from "@/components/AppShell";
import { PlusIcon, SparkIcon } from "@/components/Icons";
import { PostCard } from "@/components/PostCard";
import { useSession } from "@/components/Session";
import { Empty, PageSpinner, Sheet, Spinner } from "@/components/ui";

const PAGE_SIZE = 10;
type Filter = "all" | "me" | "partner";

export default function HomePage() {
  const { me, partner, couple } = useSession();
  const [filter, setFilter] = useState<Filter>("all");
  const author = filter === "all" ? "" : `&author=${filter}`;

  const feed = useSWRInfinite<Page<Post>>(
    (i, prev) => (prev && prev.page.number + 1 >= prev.page.totalPages ? null : `/posts?page=${i}&size=${PAGE_SIZE}${author}`),
    fetcher,
    { revalidateFirstPage: true },
  );
  const memories = useSWR<Memories>("/posts/memories/today", fetcher);

  const posts = feed.data?.flatMap((p) => p.content) ?? [];
  const last = feed.data?.[feed.data.length - 1];
  const hasMore = !!last && last.page.number + 1 < last.page.totalPages;
  const loadingMore = feed.isValidating && feed.size > (feed.data?.length ?? 0);

  const sentinel = useRef<HTMLDivElement>(null);
  const { setSize } = feed;
  const busy = useRef(false);
  busy.current = loadingMore;
  useEffect(() => {
    const el = sentinel.current;
    if (!el || !hasMore) return;
    const io = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting && !busy.current) setSize((s) => s + 1);
      },
      { rootMargin: "400px" },
    );
    io.observe(el);
    return () => io.disconnect();
  }, [hasMore, setSize]);

  const updatePost = (updated: Post) =>
    feed.mutate(
      (pages) => pages?.map((p) => ({ ...p, content: p.content.map((x) => (x.id === updated.id ? updated : x)) })),
      { revalidate: false },
    );

  return (
    <>
      <PageHeader
        title={
          <span className="flex items-baseline gap-2">
            오늘의 우리
            <span className="text-sm font-medium text-brand-strong">D+{dDay(couple.startedAt)}</span>
          </span>
        }
      />
      <div className="mx-auto max-w-2xl px-4 pb-8 md:px-8">
        {memories.data && memories.data.memories.length > 0 && <MemoriesCard data={memories.data} />}

        <div className="mb-4 mt-2 flex gap-2" role="tablist">
          {(
            [
              ["all", "모두"],
              ["me", me.nickname],
              ["partner", partner?.nickname ?? "상대"],
            ] as [Filter, string][]
          ).map(([key, label]) => (
            <button
              key={key}
              role="tab"
              aria-selected={filter === key}
              onClick={() => setFilter(key)}
              className={`h-9 rounded-full px-4 text-sm font-medium transition ${
                filter === key ? "bg-ink text-white" : "bg-white text-sub"
              }`}
            >
              {label}
            </button>
          ))}
        </div>

        {!feed.data ? (
          <PageSpinner />
        ) : posts.length === 0 ? (
          <Empty
            title={filter === "all" ? "아직 기록이 없어요" : "이 사람의 기록이 아직 없어요"}
            desc="오늘 있었던 일을 세 줄로 남겨 보세요"
            action={
              <Link href="/posts/new" className="inline-flex h-11 items-center rounded-2xl bg-brand px-5 font-semibold text-white">
                첫 기록 남기기
              </Link>
            }
          />
        ) : (
          <div className="flex flex-col gap-4">
            {posts.map((post) => (
              <PostCard key={post.id} post={post} onChange={updatePost} onDeleted={() => feed.mutate()} />
            ))}
            <div ref={sentinel} className="flex h-10 items-center justify-center">
              {loadingMore && <Spinner />}
              {!hasMore && posts.length > PAGE_SIZE && <span className="text-xs text-sub">처음 기록까지 다 봤어요</span>}
            </div>
          </div>
        )}
      </div>

      <Link
        href="/posts/new"
        className="fixed right-5 z-30 flex h-14 w-14 items-center justify-center rounded-full bg-brand text-white shadow-lg shadow-brand/30 transition active:scale-95 md:hidden"
        style={{ bottom: "calc(84px + var(--safe-bottom))" }}
        aria-label="오늘 기록하기"
      >
        <PlusIcon className="h-7 w-7" />
      </Link>
    </>
  );
}

function dDay(startedAt: string) {
  const [y, m, d] = startedAt.split("-").map(Number);
  const start = Date.UTC(y, m - 1, d);
  const [ty, tm, td] = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Seoul" }).format(new Date()).split("-").map(Number);
  return Math.floor((Date.UTC(ty, tm - 1, td) - start) / 86400000) + 1;
}

function MemoriesCard({ data }: { data: Memories }) {
  const [open, setOpen] = useState(false);
  const first = data.memories[0];
  const count = data.memories.reduce((n, m) => n + m.posts.length, 0);
  const cover = data.memories.flatMap((m) => m.posts).find((p) => p.images.length > 0)?.images[0]?.url;

  return (
    <>
      <button
        onClick={() => setOpen(true)}
        className="relative mb-4 mt-2 flex w-full items-center gap-4 overflow-hidden rounded-3xl bg-gradient-to-br from-[#ffd9de] to-[#ffeccf] p-5 text-left"
      >
        <div className="min-w-0 flex-1">
          <p className="flex items-center gap-1 text-xs font-semibold text-brand-strong">
            <SparkIcon className="h-3.5 w-3.5" /> {Number(data.today.slice(0, 4)) - first.year}년 전 오늘
          </p>
          <p className="mt-1 line-clamp-2 font-semibold leading-snug">{first.posts[0].content}</p>
          <p className="mt-2 text-xs text-sub">추억 {count}개 다시 보기</p>
        </div>
        {cover && <img src={cover} alt="" className="h-20 w-20 shrink-0 rounded-2xl object-cover" />}
      </button>
      <Sheet open={open} onClose={() => setOpen(false)} title="작년 오늘">
        <div className="flex flex-col gap-6">
          {data.memories.map((m) => (
            <section key={m.year}>
              <h3 className="mb-2 text-sm font-semibold text-brand-strong">{m.year}년</h3>
              <ul className="flex flex-col gap-2">
                {m.posts.map((p) => (
                  <li key={p.id}>
                    <Link href={`/posts/${p.id}`} className="block rounded-2xl bg-cream p-4">
                      <p className="text-xs text-sub">
                        {p.author.nickname} · {formatDay(p.postedDate, true)}
                      </p>
                      <p className="mt-1 line-clamp-3 whitespace-pre-line text-[15px]">{p.content}</p>
                    </Link>
                  </li>
                ))}
              </ul>
            </section>
          ))}
        </div>
      </Sheet>
    </>
  );
}
