"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Button, Card } from "@/components/ui";
import { useToast } from "@/components/Toast";
import { getToken } from "@/lib/auth";
import { APP_STORE_URL, normalizeInviteCode, PLAY_STORE_URL, rememberInviteCode } from "@/lib/invite";
import { copyText, isApp } from "@/lib/native";

type Platform = "app" | "ios" | "android" | "other";

function detectPlatform(): Platform {
  if (isApp()) return "app";
  const ua = navigator.userAgent;
  if (/iPhone|iPod/i.test(ua)) return "ios";
  if (/Android/i.test(ua)) return "android";
  return "other";
}

const STORE_LINK = "flex h-12 w-full items-center justify-center rounded-2xl text-[15px] font-semibold transition active:scale-[0.98]";

/**
 * 공유받은 초대 링크(/invite?code=…). 받는 사람 기기에 맞는 설치 버튼 하나와 초대코드를 보여 줌.
 * 코드는 기억해 두었다가 로그인 후 '초대코드 입력하기'에 자동으로 채움
 */
export default function InvitePage() {
  const router = useRouter();
  const toast = useToast();
  const [code, setCode] = useState("");
  const [platform, setPlatform] = useState<Platform | null>(null);

  useEffect(() => {
    const c = normalizeInviteCode(new URLSearchParams(window.location.search).get("code"));
    setCode(c);
    if (c) rememberInviteCode(c);
    setPlatform(detectPlatform());
  }, []);

  const copyCode = async () => {
    toast((await copyText(code)) ? "초대코드를 복사했어요" : "복사하지 못했어요. 코드를 직접 입력해 주세요");
  };

  // 웹에서 바로: 로그인돼 있으면 코드 입력 화면, 아니면 로그인부터 (코드는 기억해 둠)
  const startOnWeb = () => router.push(getToken() ? "/onboarding/couple" : "/login");

  return (
    <main className="pt-safe pb-safe mx-auto flex min-h-dvh max-w-md flex-col px-6">
      <div className="flex flex-col items-center pt-14 text-center">
        <img src="/icon.svg" alt="" className="h-16 w-16 drop-shadow-sm" />
        <h1 className="mt-5 text-2xl font-bold leading-snug">
          오늘의 우리에
          <br />
          초대받았어요
        </h1>
        <p className="mt-2 text-sub">둘이서 하루를 세 줄로 남기는 일기장이에요.</p>
      </div>

      {code && (
        <Card className="mt-8 text-center">
          <p className="text-sm text-sub">초대코드</p>
          <button
            type="button"
            onClick={copyCode}
            className="mt-2 rounded-2xl px-3 py-1 text-brand-strong transition active:scale-95 active:bg-brand/10"
            aria-label={`초대코드 ${code} 복사`}
          >
            {/* 전역 button { font: inherit } 때문에 글자 스타일은 span 에 */}
            <span className="block pl-[0.3em] font-mono text-3xl font-bold tracking-[0.3em]">{code}</span>
          </button>
          <p className="text-xs text-sub">코드를 누르면 복사돼요</p>
        </Card>
      )}

      <ol className="mt-6 space-y-1 px-1 text-sm text-sub">
        <li>1. {platform === "app" ? "아래 버튼을 눌러요" : "앱을 받거나 웹에서 시작해요"}</li>
        <li>2. 로그인하고 &lsquo;초대코드 입력하기&rsquo;에 위 코드를 넣어요</li>
        <li>3. &lsquo;연결하기&rsquo;를 누르면 둘이 이어져요</li>
      </ol>

      <div className="mt-auto flex flex-col gap-3 pb-10 pt-8">
        {platform === "app" && (
          <Button full onClick={startOnWeb}>
            초대코드 입력하러 가기
          </Button>
        )}
        {platform === "ios" && (
          <a href={APP_STORE_URL} className={`${STORE_LINK} bg-black text-white`}>
            App Store에서 받기
          </a>
        )}
        {platform === "android" && (
          <a href={PLAY_STORE_URL} className={`${STORE_LINK} bg-black text-white`}>
            Google Play에서 받기
          </a>
        )}
        {(platform === "ios" || platform === "android") && (
          <Button full variant="soft" onClick={startOnWeb}>
            설치 없이 웹에서 시작하기
          </Button>
        )}
        {platform === "other" && (
          <>
            <Button full onClick={startOnWeb}>
              웹에서 시작하기
            </Button>
            <p className="text-center text-xs text-sub">
              휴대폰 앱:{" "}
              <a href={APP_STORE_URL} className="underline">
                App Store
              </a>
              {" · "}
              <a href={PLAY_STORE_URL} className="underline">
                Google Play
              </a>
            </p>
          </>
        )}
      </div>
    </main>
  );
}
