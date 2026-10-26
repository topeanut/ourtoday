"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { KakaoSymbol } from "@/components/Icons";
import { getToken, startKakaoLogin } from "@/lib/auth";

export default function LoginPage() {
  const router = useRouter();

  useEffect(() => {
    if (getToken()) router.replace("/");
  }, [router]);

  return (
    <main className="pt-safe pb-safe flex min-h-dvh flex-col items-center px-6">
      <div className="flex flex-1 flex-col items-center justify-center text-center">
        <img src="/icon.svg" alt="" className="mb-6 h-20 w-20 drop-shadow-sm" />
        <h1 className="text-3xl font-bold tracking-tight">오늘의 우리</h1>
        <p className="mt-3 leading-relaxed text-sub">
          길게 쓰지 않아도 괜찮아요.
          <br />세 줄이면 충분한 우리 둘만의 일기장
        </p>
      </div>
      <div className="w-full max-w-sm pb-10">
        <button
          onClick={startKakaoLogin}
          className="flex h-14 w-full items-center justify-center gap-2 rounded-2xl bg-[#FEE500] text-[15px] font-semibold text-black/85 transition active:scale-[0.98]"
        >
          <KakaoSymbol />
          카카오로 시작하기
        </button>
        <p className="mt-4 text-center text-xs leading-relaxed text-sub">
          로그인하면 서비스 이용에 동의하는 것으로 봐요.
          <br />
          카카오에서는 회원 식별값만 받아요.
        </p>
      </div>
    </main>
  );
}
