"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { AppleSymbol, KakaoSymbol } from "@/components/Icons";
import { KeyboardSafeScreen } from "@/components/KeyboardSafeScreen";
import { Spinner } from "@/components/ui";
import { api, ApiError, errorMessage } from "@/lib/api";
import { AppleLoginCancelled, canUseAppleLogin, preloadAppleSdk, signInWithApple } from "@/lib/apple";
import { getToken, setTokens, takeReturnTo } from "@/lib/auth";
import { IS_DEV } from "@/lib/env";
import { preloadKakaoSdk, startKakaoLogin } from "@/lib/kakao";
import type { OnboardingStatus } from "@/lib/types";

/** 로그인 직후 가입 단계에 맞는 화면으로 */
async function goAfterLogin(router: ReturnType<typeof useRouter>) {
  const { status } = await api<{ status: OnboardingStatus }>("/users/me/onboarding-status");
  if (status === "NEED_PROFILE") router.replace("/onboarding/profile");
  else if (status === "CONNECTED") router.replace(takeReturnTo() ?? "/");
  else router.replace("/onboarding/couple");
}

export default function LoginPage() {
  const router = useRouter();
  const [showApple, setShowApple] = useState(false);
  const [appleBusy, setAppleBusy] = useState(false);
  const [appleError, setAppleError] = useState("");

  useEffect(() => {
    if (getToken()) router.replace("/");
    const apple = canUseAppleLogin();
    setShowApple(apple);
    // 웹 브라우저는 버튼을 누른 뒤 SDK 를 받으면 그만큼 늦어지므로 미리 받아 둠 (iOS 앱은 네이티브라 불필요)
    if (apple) preloadAppleSdk();
    // 휴대폰은 카카오톡 앱으로 바로 로그인하므로 SDK 를 미리 받아 둠
    preloadKakaoSdk();
  }, [router]);

  const loginWithApple = async () => {
    setAppleBusy(true);
    setAppleError("");
    try {
      const t = await signInWithApple();
      setTokens(t.accessToken, t.refreshToken);
      await goAfterLogin(router);
    } catch (err) {
      if (!(err instanceof AppleLoginCancelled)) {
        // apple.ts 의 안내 문구는 그대로, 서버 오류는 공통 문구로
        setAppleError(err instanceof Error && !(err instanceof ApiError) && !(err instanceof TypeError) ? err.message : errorMessage(err));
      }
      setAppleBusy(false);
    }
  };

  return (
    // 키보드가 열려도 화면이 밀리지 않게 보이는 영역에 맞추고, 열린 동안은 아이콘·소개 문구를 접음
    <KeyboardSafeScreen className="pt-safe pb-safe flex flex-col items-center px-6">
      <div className="flex flex-1 flex-col items-center justify-center text-center group-data-[keyboard=open]:flex-none group-data-[keyboard=open]:py-4">
        <img src="/icon.svg" alt="" className="mb-6 h-20 w-20 drop-shadow-sm group-data-[keyboard=open]:hidden" />
        <h1 className="text-3xl font-bold tracking-tight">오늘의 우리</h1>
        <p className="mt-3 leading-relaxed text-sub group-data-[keyboard=open]:hidden">
          길게 쓰지 않아도 괜찮아요.
          <br />세 줄이면 충분한 우리 둘만의 일기장
        </p>
      </div>
      <div className="w-full max-w-sm pb-10 group-data-[keyboard=open]:pb-3">
        <button
          onClick={() => void startKakaoLogin()}
          className="flex h-14 w-full items-center justify-center gap-2 rounded-2xl bg-[#FEE500] text-[15px] font-semibold text-black/85 transition active:scale-[0.98]"
        >
          <KakaoSymbol />
          카카오로 시작하기
        </button>
        {showApple && (
          <button
            onClick={loginWithApple}
            disabled={appleBusy}
            aria-busy={appleBusy}
            className="mt-3 flex h-14 w-full items-center justify-center gap-2 rounded-2xl bg-black text-[15px] font-semibold text-white transition active:scale-[0.98] disabled:opacity-80"
          >
            {/* iOS 시스템 Apple 로그인 창은 뜨기까지 몇 초 걸릴 수 있어서, 누르자마자 진행 중임을 보여 줌 */}
            {appleBusy ? (
              <>
                <Spinner className="h-4 w-4 border-white/30 border-t-white" />
                Apple 로그인 여는 중…
              </>
            ) : (
              <>
                <AppleSymbol />
                Apple로 계속하기
              </>
            )}
          </button>
        )}
        {appleError && <p className="mt-2 text-center text-xs text-[#d6404e]">{appleError}</p>}
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
          카카오·Apple에서는 회원 식별값만 받아요.
        </p>
      </div>
    </KeyboardSafeScreen>
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
      await goAfterLogin(router);
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
          autoCapitalize="none"
          autoCorrect="off"
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
