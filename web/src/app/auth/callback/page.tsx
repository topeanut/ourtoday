"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { api } from "@/lib/api";
import { setTokens, takeReturnTo } from "@/lib/auth";
import type { OnboardingStatus } from "@/lib/types";
import { Button, PageSpinner } from "@/components/ui";

/** 백엔드가 카카오 로그인 후 /auth/callback#accessToken=... 로 보냄 (토큰이 서버 로그에 안 남게 fragment) */
export default function AuthCallbackPage() {
  const router = useRouter();
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    const params = new URLSearchParams(window.location.hash.slice(1));
    const token = params.get("accessToken");
    history.replaceState(null, "", window.location.pathname);
    if (!token) {
      setFailed(true);
      return;
    }
    setTokens(token, params.get("refreshToken"));
    api<{ status: OnboardingStatus }>("/users/me/onboarding-status")
      .then(({ status }) => {
        if (status === "NEED_PROFILE") router.replace("/onboarding/profile");
        else if (status === "CONNECTED") router.replace(takeReturnTo() ?? "/");
        else router.replace("/onboarding/couple");
      })
      .catch(() => setFailed(true));
  }, [router]);

  if (!failed) return <PageSpinner />;
  return (
    <main className="flex min-h-dvh flex-col items-center justify-center gap-4 px-6 text-center">
      <p className="font-semibold">로그인에 실패했어요</p>
      <Button onClick={() => router.replace("/login")}>다시 로그인</Button>
    </main>
  );
}
