"use client";

import { use } from "react";
import { useRouter } from "next/navigation";
import useSWR from "swr";
import { api, fetcher } from "@/lib/api";
import type { Post } from "@/lib/types";
import { PageHeader } from "@/components/AppShell";
import { BackIcon } from "@/components/Icons";
import { PostForm } from "@/components/PostForm";
import { useSession } from "@/components/Session";
import { useToast } from "@/components/Toast";
import { Empty, PageSpinner } from "@/components/ui";

export default function EditPostPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const router = useRouter();
  const toast = useToast();
  const { me } = useSession();
  const { data } = useSWR<Post>(`/posts/${id}`, fetcher);

  return (
    <>
      <PageHeader
        title="기록 수정"
        left={
          <button onClick={() => router.back()} className="-ml-2 rounded-full p-2 hover:bg-black/5" aria-label="뒤로">
            <BackIcon />
          </button>
        }
      />
      <div className="mx-auto max-w-2xl px-4 pb-10 md:px-8">
        {!data ? (
          <PageSpinner />
        ) : data.author.id !== me.id ? (
          <Empty title="내가 쓴 기록만 수정할 수 있어요" />
        ) : (
          <PostForm
            initial={data}
            submitLabel="저장"
            onSubmit={async (value) => {
              await api<Post>(`/posts/${id}`, { method: "PATCH", body: value });
              toast("수정했어요");
              router.replace(`/posts/${id}`);
            }}
          />
        )}
      </div>
    </>
  );
}
