/**
 * 로딩 중 자리 잡기용 스켈레톤.
 * 실제 카드와 같은 크기로 그려서 데이터가 들어와도 레이아웃이 밀리지 않게 함 (PostCard·버킷 행·댓글 행 기준)
 */

export function Skeleton({ className = "" }: { className?: string }) {
  return <span className={`block animate-pulse rounded-full bg-black/[0.06] ${className}`} aria-hidden />;
}

function Loading({ children, className = "" }: { children: React.ReactNode; className?: string }) {
  return (
    <div className={`animate-fade ${className}`} role="status" aria-label="불러오는 중">
      {children}
    </div>
  );
}

/** PostCard 모양 (아바타·이름 줄·본문 2줄·반응 줄) */
function PostCardSkeleton({ withComment = true }: { withComment?: boolean }) {
  return (
    <div className="rounded-3xl bg-white p-5 shadow-[0_1px_2px_rgba(43,35,38,0.04),0_8px_24px_rgba(43,35,38,0.04)]">
      <div className="mb-3 flex items-center gap-3">
        <Skeleton className="h-9 w-9" />
        <div className="flex flex-col gap-1.5">
          <Skeleton className="h-3.5 w-16" />
          <Skeleton className="h-3 w-24" />
        </div>
      </div>
      <Skeleton className="h-4 w-11/12" />
      <Skeleton className="mt-2 h-4 w-3/5" />
      <div className="mt-4 flex gap-1.5">
        {[0, 1, 2, 3, 4].map((i) => (
          <Skeleton key={i} className="h-9 w-12" />
        ))}
      </div>
      {withComment && (
        <div className="mt-4 border-t border-line pt-3">
          <Skeleton className="h-3 w-16" />
        </div>
      )}
    </div>
  );
}

export function FeedSkeleton() {
  return (
    <Loading className="flex flex-col gap-4">
      <PostCardSkeleton />
      <PostCardSkeleton />
      <PostCardSkeleton />
    </Loading>
  );
}

export function BucketSkeleton() {
  return (
    <Loading className="flex flex-col gap-2">
      {[0, 1, 2, 3].map((i) => (
        <div key={i} className="flex items-start gap-3 rounded-3xl bg-white p-4 shadow-sm">
          <Skeleton className="mt-0.5 h-7 w-7 shrink-0" />
          <div className="flex flex-1 flex-col gap-2 pt-1">
            <Skeleton className={`h-4 ${i % 2 ? "w-2/5" : "w-3/5"}`} />
            <Skeleton className="h-3 w-20" />
          </div>
        </div>
      ))}
    </Loading>
  );
}

/** 댓글 행 모양 (아바타 32·이름·본문 1줄) */
export function CommentsSkeleton() {
  return (
    <Loading className="divide-y divide-line">
      {[0, 1].map((i) => (
        <div key={i} className="flex gap-3 px-1 py-3.5">
          <Skeleton className="h-8 w-8 shrink-0" />
          <div className="flex flex-1 flex-col gap-2 pt-0.5">
            <Skeleton className="h-3.5 w-20" />
            <Skeleton className={`h-4 ${i ? "w-1/2" : "w-4/5"}`} />
          </div>
        </div>
      ))}
    </Loading>
  );
}

export function PostDetailSkeleton() {
  return (
    <Loading>
      <PostCardSkeleton withComment={false} />
      <div className="mt-6">
        <Skeleton className="mb-1 ml-1 h-4 w-10" />
        <CommentsSkeleton />
      </div>
    </Loading>
  );
}
