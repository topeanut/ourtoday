"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import useSWR, { mutate } from "swr";
import { api, errorMessage, fetcher } from "@/lib/api";
import { getToken, logout, takeReturnTo } from "@/lib/auth";
import { share } from "@/lib/native";
import type { InviteResponse, OnboardingStatus } from "@/lib/types";
import { Button, Card, Field, inputClass } from "@/components/ui";
import { useToast } from "@/components/Toast";

export default function CoupleOnboardingPage() {
  const router = useRouter();
  const toast = useToast();
  const [mode, setMode] = useState<"choose" | "invite" | "join">("choose");
  const [invite, setInvite] = useState<InviteResponse | null>(null);
  const [code, setCode] = useState("");
  const [busy, setBusy] = useState(false);

  // 초대코드를 띄워둔 동안 상대가 합류했는지 3초마다 확인
  const { data, isValidating } = useSWR<{ status: OnboardingStatus }>(
    getToken() ? "/users/me/onboarding-status" : null,
    fetcher,
    { refreshInterval: mode === "invite" ? 3000 : 0, refreshWhenHidden: true },
  );

  useEffect(() => {
    if (!getToken()) router.replace("/login");
    // 오래된 캐시 값으로 잘못 이동하지 않게 최신 값일 때만
    if (isValidating) return;
    if (data?.status === "NEED_PROFILE") router.replace("/onboarding/profile");
    if (data?.status === "CONNECTED") router.replace(takeReturnTo() ?? "/");
  }, [data, isValidating, router]);

  const createInvite = async () => {
    setBusy(true);
    try {
      setInvite(await api<InviteResponse>("/couples/invite", { method: "POST" }));
      setMode("invite");
    } catch (e) {
      toast(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const join = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    try {
      await api("/couples/join", { method: "POST", body: { inviteCode: code.trim() } });
      await mutate("/users/me/onboarding-status");
    } catch (err) {
      toast(errorMessage(err));
      setBusy(false);
    }
  };

  const shareCode = async () => {
    if (!invite) return;
    const result = await share({
      title: "오늘의 우리 초대",
      text: `오늘의 우리에서 같이 기록해요! 초대코드: ${invite.inviteCode}`,
      url: window.location.origin,
    });
    if (result === "copied") toast("초대 메시지를 복사했어요");
    else if (result === "failed") toast("복사하지 못했어요. 화면의 코드를 직접 알려 주세요");
  };

  return (
    <div className="flex flex-1 flex-col pt-16">
      <p className="text-sm font-semibold text-brand-strong">2 / 2</p>
      <h1 className="mt-2 text-2xl font-bold leading-snug">
        우리 둘을
        <br />
        연결해 주세요
      </h1>
      <p className="mt-2 text-sub">한 사람이 초대코드를 만들고, 다른 한 사람이 입력하면 돼요.</p>

      {mode === "choose" && (
        <div className="mt-10 flex flex-col gap-3">
          <button onClick={createInvite} disabled={busy} className="rounded-3xl bg-white p-5 text-left shadow-sm transition active:scale-[0.99]">
            <p className="font-semibold">초대코드 만들기</p>
            <p className="mt-1 text-sm text-sub">코드를 만들어 상대에게 보내요</p>
          </button>
          <button onClick={() => setMode("join")} className="rounded-3xl bg-white p-5 text-left shadow-sm transition active:scale-[0.99]">
            <p className="font-semibold">초대코드 입력하기</p>
            <p className="mt-1 text-sm text-sub">상대에게 받은 코드가 있어요</p>
          </button>
          {data?.status === "PENDING_INVITE" && (
            <p className="px-1 text-sm text-sub">이미 만든 코드가 있다면 새로 만들면 이전 코드는 사용할 수 없어요.</p>
          )}
        </div>
      )}

      {mode === "invite" && invite && (
        <Card className="mt-10 text-center">
          <p className="text-sm text-sub">초대코드</p>
          <p className="mt-2 font-mono text-4xl font-bold tracking-[0.3em] text-brand-strong">{invite.inviteCode}</p>
          <p className="mt-3 text-xs text-sub">
            {new Date(invite.expiresAt).toLocaleString("ko-KR", { month: "long", day: "numeric", hour: "numeric", minute: "2-digit" })}
            까지 유효
          </p>
          <Button full className="mt-6" onClick={shareCode}>
            상대에게 보내기
          </Button>
          <p className="mt-4 flex items-center justify-center gap-2 text-sm text-sub">
            <span className="h-2 w-2 animate-pulse rounded-full bg-brand" />
            상대가 입력하면 자동으로 넘어가요
          </p>
          <button className="mt-4 text-sm text-sub underline" onClick={() => setMode("choose")}>
            뒤로
          </button>
        </Card>
      )}

      {mode === "join" && (
        <form onSubmit={join} className="mt-10 flex flex-col gap-4">
          <Field label="초대코드">
            <input
              className={`${inputClass} text-center font-mono text-2xl tracking-[0.3em] uppercase`}
              value={code}
              onChange={(e) => setCode(e.target.value.replace(/[^0-9a-zA-Z]/g, "").slice(0, 8).toUpperCase())}
              placeholder="ABC234"
              autoFocus
              autoCapitalize="characters"
              autoComplete="off"
            />
          </Field>
          <Button type="submit" full loading={busy} disabled={code.length < 6}>
            연결하기
          </Button>
          <button type="button" className="text-sm text-sub underline" onClick={() => setMode("choose")}>
            뒤로
          </button>
        </form>
      )}

      <div className="mt-auto pb-8 pt-10 text-center">
        <button className="text-sm text-sub" onClick={logout}>
          다른 계정으로 로그인
        </button>
      </div>
    </div>
  );
}
