"use client";

import { use } from "react";
import { useRouter } from "next/navigation";
import useSWR from "swr";
import { api, fetcher, revalidateAll } from "@/lib/api";
import { goBack } from "@/lib/nav";
import type { Post } from "@/lib/types";
import { PageHeader } from "@/components/AppShell";
import { KeyboardSafeScreen } from "@/components/KeyboardSafeScreen";
import { BackIcon } from "@/components/Icons";
import { PostForm } from "@/components/PostForm";
import { useSession } from "@/components/Session";
import { useToast } from "@/components/Toast";
import { Empty, IconButton, PageSpinner } from "@/components/ui";

export default function EditPostPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const router = useRouter();
  const toast = useToast();
  const { me } = useSession();
  const { data } = useSWR<Post>(`/posts/${id}`, fetcher);

  return (
    // 모바일은 키보드가 열리면 화면을 보이는 영역에 맞춰서 '기록하기' 버튼이 가려지지 않게 함
    <KeyboardSafeScreen as="div" mobileOnly className="z-30 flex flex-col bg-cream">
      <PageHeader
        title="기록 수정"
        left={
          <IconButton onClick={() => goBack(router, `/posts/${id}`)} label="뒤로" className="-ml-2.5">
            <BackIcon />
          </IconButton>
        }
      />
      <div className="mx-auto flex w-full max-w-2xl flex-1 flex-col px-4 pb-[calc(16px+var(--safe-bottom))] group-data-[keyboard=open]:pb-3 md:px-8 md:pb-10">
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
              await revalidateAll();
              router.replace(`/posts/${id}`);
            }}
          />
        )}
      </div>
    </KeyboardSafeScreen>
  );
}
