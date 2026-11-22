"use client";

import { useEffect, useState } from "react";
import { getPushState, requestPushPermission } from "@/lib/push";
import { useToast } from "@/components/Toast";
import { Button, Sheet } from "@/components/ui";

const ASKED_KEY = "ourtoday.pushAsked";

/** 앱에서 권한이 아직 정해지지 않았을 때만 true (웹 브라우저·옛 앱은 false) */
function useCanAsk() {
  const [can, setCan] = useState(false);
  useEffect(() => {
    let alive = true;
    void getPushState().then((s) => {
      if (alive) setCan(s.supported && s.permission === "default");
    });
    return () => {
      alive = false;
    };
  }, []);
  return [can, setCan] as const;
}

/** 초대코드 카드 아래: 상대가 들어오면 알림을 받을 수 있게 권한을 묻는 한 줄 */
export function PushInviteNudge() {
  const toast = useToast();
  const [can, setCan] = useCanAsk();
  const [busy, setBusy] = useState(false);

  if (!can) return null;

  const ask = async () => {
    setBusy(true);
    const s = await requestPushPermission();
    setBusy(false);
    if (s.permission === "granted") toast("알림을 켰어요");
    if (s.permission !== "default") setCan(false);
  };

  return (
    <div className="mt-3 flex items-center justify-between gap-3 rounded-2xl bg-white px-4 py-3 shadow-sm">
      <p className="text-sm text-sub">상대가 들어오면 알려 드릴게요</p>
      <Button variant="soft" className="h-9 shrink-0 px-4 text-sm" loading={busy} onClick={ask}>
        알림 받기
      </Button>
    </div>
  );
}

/** 홈 첫 진입 시 한 번만 알림 권한 안내 시트 */
export function PushWelcomeSheet() {
  const toast = useToast();
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let alive = true;
    try {
      if (localStorage.getItem(ASKED_KEY)) return;
    } catch {
      return;
    }
    void getPushState().then((s) => {
      if (alive && s.supported && s.permission === "default") setOpen(true);
    });
    return () => {
      alive = false;
    };
  }, []);

  const close = () => {
    try {
      localStorage.setItem(ASKED_KEY, "1");
    } catch {
      // 저장 못 해도 이번 세션에서만 다시 안 뜸
    }
    setOpen(false);
  };

  const ask = async () => {
    setBusy(true);
    const s = await requestPushPermission();
    setBusy(false);
    if (s.permission === "granted") toast("알림을 켰어요");
    close();
  };

  return (
    <Sheet open={open} onClose={close} title="알림을 받아 볼까요?">
      <p className="text-sm leading-relaxed text-sub">상대가 기록을 남기거나 내 기록에 반응하면 바로 알려 드려요. 기록 내용은 알림에 보이지 않아요.</p>
      <div className="mt-6 flex flex-col gap-2">
        <Button full loading={busy} onClick={ask}>
          알림 받기
        </Button>
        <Button variant="ghost" full disabled={busy} onClick={close}>
          나중에
        </Button>
      </div>
    </Sheet>
  );
}
