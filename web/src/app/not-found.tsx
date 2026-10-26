import Link from "next/link";

export default function NotFound() {
  return (
    <main className="flex min-h-dvh flex-col items-center justify-center gap-3 px-6 text-center">
      <p className="text-5xl">🌙</p>
      <p className="font-semibold">찾는 페이지가 없어요</p>
      <Link href="/" className="font-semibold text-brand-strong">
        홈으로
      </Link>
    </main>
  );
}
