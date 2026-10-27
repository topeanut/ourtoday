"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { KakaoSymbol } from "@/components/Icons";
import { api, errorMessage } from "@/lib/api";
import { getToken, setTokens, startKakaoLogin, takeReturnTo } from "@/lib/auth";
import { IS_DEV } from "@/lib/env";
import type { OnboardingStatus } from "@/lib/types";

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
        {IS_DEV && <DevLogin />}
        <p className="mt-4 text-center text-xs leading-relaxed text-sub">
          로그인하면{" "}
          <a href="https://ourtoday-app.vercel.app/terms" className="underline">
            이용약관
          </a>
          과{" "}
          <a href="https://ourtoday-app.vercel.app/privacy" className="underline">
            개인정보처리방침
          </a>
          에 동의하는 것으로 봐요.
          <br />
          카카오에서는 회원 식별값만 받아요.
        </p>
      </div>
    </main>
  );
}

/** 개발 서버 전용: 이름만으로 로그인 (같은 이름 = 같은 계정) */
function DevLogin() {
  const router = useRouter();
  const [name, setName] = useState("qa-a");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      const t = await api<{ accessToken: string; refreshToken: string }>("/dev/login", { method: "POST", body: { name } });
      setTokens(t.accessToken, t.refreshToken);
      const { status } = await api<{ status: OnboardingStatus }>("/users/me/onboarding-status");
      if (status === "NEED_PROFILE") router.replace("/onboarding/profile");
      else if (status === "CONNECTED") router.replace(takeReturnTo() ?? "/");
      else router.replace("/onboarding/couple");
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  };

  return (
    <form onSubmit={submit} className="mt-3 rounded-2xl border-2 border-dashed border-[#6fb3e8] bg-white p-3">
      <p className="mb-2 text-xs font-semibold text-[#3b7fbf]">개발 서버 임시 로그인</p>
      <div className="flex gap-2">
        <input
          aria-label="개발 계정 이름"
          className="min-w-0 flex-1 rounded-xl border border-line px-3 py-2"
          value={name}
          onChange={(e) => setName(e.target.value.replace(/[^a-zA-Z0-9_-]/g, "").slice(0, 20))}
        />
        <button disabled={busy || !name} className="rounded-xl bg-[#3b7fbf] px-4 font-semibold text-white disabled:opacity-50">
          로그인
        </button>
      </div>
      {error && <p className="mt-2 text-xs text-[#d6404e]">{error}</p>}
    </form>
  );
}
