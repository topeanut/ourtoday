"use client";

import { Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import useSWR from "swr";
import { api, fetcher } from "@/lib/api";
import { goBack } from "@/lib/nav";
import type { BucketItem, Post } from "@/lib/types";
import { PageHeader } from "@/components/AppShell";
import { BackIcon } from "@/components/Icons";
import { PostForm } from "@/components/PostForm";
import { useToast } from "@/components/Toast";

export default function NewPostPage() {
  return (
    <Suspense>
      <NewPost />
    </Suspense>
  );
}

/** ?bucket=ID 로 들어오면 작성 후 그 버킷리스트를 이 글과 함께 완료 처리 */
function NewPost() {
  const router = useRouter();
  const toast = useToast();
  const bucketId = useSearchParams().get("bucket");
  const bucket = useSWR<{ items: BucketItem[] }>(bucketId ? "/bucket-items?status=ALL" : null, fetcher);
  const item = bucket.data?.items.find((i) => i.id === bucketId);

  return (
    <>
      <PageHeader
        title={item ? "버킷리스트 달성 기록" : "오늘 기록하기"}
        left={
          <button onClick={() => goBack(router, bucketId ? "/bucket" : "/")} className="-ml-2 rounded-full p-2 hover:bg-black/5" aria-label="뒤로">
            <BackIcon />
          </button>
        }
      />
      <div className="mx-auto max-w-2xl px-4 pb-10 md:px-8">
        {item && (
          <p className="mb-3 rounded-2xl bg-brand-soft px-4 py-3 text-sm text-brand-strong">
            ✅ <b>{item.title}</b> 을(를) 이 기록과 함께 완료해요
          </p>
        )}
        <PostForm
          submitLabel="기록하기"
          onSubmit={async (value) => {
            const post = await api<Post>("/posts", { method: "POST", body: value });
            let completed = true;
            if (bucketId) {
              completed = await api(`/bucket-items/${bucketId}/complete`, { method: "PATCH", body: { postId: post.id } })
                .then(() => true)
                .catch(() => false);
            }
            toast(completed ? "기록했어요" : "기록은 남겼지만 버킷리스트 완료 처리에 실패했어요");
            router.replace(bucketId ? "/bucket" : "/");
          }}
        />
      </div>
    </>
  );
}
