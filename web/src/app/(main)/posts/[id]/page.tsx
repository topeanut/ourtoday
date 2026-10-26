"use client";

import { use } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import useSWR from "swr";
import { ApiError, fetcher } from "@/lib/api";
import { goBack } from "@/lib/nav";
import type { Post } from "@/lib/types";
import { PageHeader } from "@/components/AppShell";
import { BackIcon } from "@/components/Icons";
import { PostCard } from "@/components/PostCard";
import { Empty, PageSpinner } from "@/components/ui";

export default function PostDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const router = useRouter();
  const { data, error, mutate } = useSWR<Post>(`/posts/${id}`, fetcher, { shouldRetryOnError: false });

  const back = () => goBack(router);

  return (
    <>
      <PageHeader
        title="기록"
        left={
          <button onClick={back} className="-ml-2 rounded-full p-2 hover:bg-black/5" aria-label="뒤로">
            <BackIcon />
          </button>
        }
      />
      <div className="mx-auto max-w-2xl px-4 pb-10 md:px-8">
        {error ? (
          <Empty
            title={error instanceof ApiError && error.code === "FORBIDDEN" ? "볼 수 없는 기록이에요" : "기록을 찾을 수 없어요"}
            desc="지워졌거나 우리 커플의 기록이 아니에요"
            action={
              <Link href="/" className="font-semibold text-brand-strong">
                홈으로
              </Link>
            }
          />
        ) : !data ? (
          <PageSpinner />
        ) : (
          <PostCard post={data} onChange={(p) => mutate(p, { revalidate: false })} onDeleted={() => router.replace("/")} linkToDetail={false} />
        )}
      </div>
    </>
  );
}
