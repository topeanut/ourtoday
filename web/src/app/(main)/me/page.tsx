"use client";

import { useState } from "react";
import useSWR from "swr";
import { api, errorMessage, fetcher } from "@/lib/api";
import { clearToken, logout } from "@/lib/auth";
import { formatDay, todayKst } from "@/lib/date";
import { isApp, openExternal } from "@/lib/native";
import type { CoupleStats, MemberStats, Settings } from "@/lib/types";
import { PageHeader } from "@/components/AppShell";
import { ChevronRight } from "@/components/Icons";
import { useSession } from "@/components/Session";
import { useToast } from "@/components/Toast";
import { Avatar, Button, Card, Confirm, DateField, Field, inputClass, Sheet, Spinner, Toggle } from "@/components/ui";

export default function MyPage() {
  const { me, partner, couple, refresh } = useSession();
  const toast = useToast();
  const stats = useSWR<CoupleStats>("/couples/me/stats", fetcher);
  const settings = useSWR<Settings>("/users/me/settings", fetcher);

  const [editName, setEditName] = useState(false);
  const [editDate, setEditDate] = useState(false);
  const [confirm, setConfirm] = useState<"break" | "withdraw" | null>(null);
  const [busy, setBusy] = useState(false);

  const updateSetting = async (patch: Partial<Settings>) => {
    if (!settings.data) return;
    const next = { ...settings.data, ...patch };
    settings.mutate(next, { revalidate: false });
    try {
      await api("/users/me/settings", { method: "PATCH", body: next });
    } catch (e) {
      settings.mutate();
      toast(errorMessage(e));
    }
  };

  const exportData = async () => {
    setBusy(true);
    try {
      const { downloadUrl } = await api<{ downloadUrl: string }>("/users/me/export", { method: "POST" });
      toast("내보내기 파일을 만들었어요");
      openExternal(downloadUrl);
    } catch (e) {
      toast(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const danger = async () => {
    setBusy(true);
    try {
      if (confirm === "break") {
        await api("/couples/me", { method: "DELETE" });
        window.location.replace("/onboarding/couple");
      } else {
        await api("/users/me", { method: "DELETE" });
        clearToken();
        window.location.replace("/login");
      }
    } catch (e) {
      toast(errorMessage(e));
      setBusy(false);
    }
  };

  return (
    <>
      <PageHeader title="마이페이지" />
      <div className="mx-auto flex max-w-2xl flex-col gap-4 px-4 pb-10 md:px-8">
        <Card>
          <div className="flex items-center gap-4">
            <div className="flex -space-x-3">
              <Avatar name={me.nickname} seed={me.id} size={52} />
              {partner && <Avatar name={partner.nickname} seed={partner.id} size={52} />}
            </div>
            <div className="min-w-0 flex-1">
              <p className="truncate text-lg font-bold">
                {me.nickname} <span className="text-brand">♥</span> {partner?.nickname}
              </p>
              <button className="block text-left text-sm text-sub" onClick={() => setEditDate(true)}>
                <b className="text-brand-strong">{stats.data?.couple.daysTogether ?? "-"}일째</b>
                <span className="block text-xs">{formatDay(couple.startedAt, true)}부터</span>
              </button>
            </div>
          </div>
          <div className="mt-4 grid grid-cols-2 gap-2">
            <Button variant="soft" className="h-10 text-sm" onClick={() => setEditName(true)}>
              닉네임 변경
            </Button>
            <Button variant="soft" className="h-10 text-sm" onClick={() => setEditDate(true)}>
              사귄 날짜 변경
            </Button>
          </div>
        </Card>

        <Card>
          <h2 className="mb-4 font-bold">우리의 기록</h2>
          {!stats.data ? (
            <div className="flex justify-center py-6">
              <Spinner />
            </div>
          ) : (
            <>
              <div className="grid grid-cols-[1fr_auto_auto] items-center gap-x-6 gap-y-3 text-sm">
                <span />
                <span className="w-16 text-center text-xs font-semibold text-sub">{me.nickname}</span>
                <span className="w-16 text-center text-xs font-semibold text-sub">{partner?.nickname}</span>
                <StatRow label="전체 기록" pick={(s) => s.totalPosts} unit="개" stats={stats.data} />
                <StatRow label="이번 달" pick={(s) => s.thisMonthPosts} unit="개" stats={stats.data} />
                <StatRow label="연속 기록" pick={(s) => s.consecutiveDays} unit="일" stats={stats.data} highlight />
                <StatRow label="메모·일정" pick={(s) => s.totalMemos} unit="개" stats={stats.data} />
                <StatRow label="남긴 반응" pick={(s) => s.totalReactionsGiven} unit="개" stats={stats.data} />
              </div>
              {stats.data.couple.firstPostDate && (
                <p className="mt-4 border-t border-line pt-4 text-xs text-sub">첫 기록 {formatDay(stats.data.couple.firstPostDate, true)}</p>
              )}
            </>
          )}
        </Card>

        <Card>
          <h2 className="mb-1 font-bold">알림</h2>
          <p className="mb-3 text-xs text-sub">앱 푸시 알림은 준비 중이에요. 미리 원하는 알림을 골라 두세요.</p>
          {settings.data ? (
            <div className="flex flex-col divide-y divide-line">
              <label className="flex items-center justify-between py-3">
                <span>작년 오늘 알림</span>
                <Toggle label="작년 오늘 알림" checked={settings.data.notifyMemoryToday} onChange={(v) => updateSetting({ notifyMemoryToday: v })} />
              </label>
              <label className="flex items-center justify-between py-3">
                <span>상대가 기록하면 알림</span>
                <Toggle label="상대가 기록하면 알림" checked={settings.data.notifyPartnerPost} onChange={(v) => updateSetting({ notifyPartnerPost: v })} />
              </label>
            </div>
          ) : (
            <Spinner />
          )}
        </Card>

        <Card className="p-2">
          <MenuRow onClick={exportData} label="내 데이터 내보내기" desc="기록·사진·메모를 zip 파일로" right={busy && !confirm ? <Spinner /> : undefined} />
          <MenuRow onClick={logout} label="로그아웃" />
        </Card>

        <Card className="p-2">
          <MenuRow onClick={() => setConfirm("break")} label="커플 연결 해제" danger />
          <MenuRow onClick={() => setConfirm("withdraw")} label="회원 탈퇴" danger />
        </Card>

        <p className="pb-4 text-center text-xs text-sub">오늘의 우리 {isApp() ? "앱" : "웹"} · v1.0</p>
      </div>

      <NicknameSheet open={editName} current={me.nickname} onClose={() => setEditName(false)} onSaved={() => { setEditName(false); refresh(); }} />
      <StartDateSheet
        open={editDate}
        current={couple.startedAt}
        onClose={() => setEditDate(false)}
        onSaved={() => {
          setEditDate(false);
          refresh();
          stats.mutate();
        }}
      />
      <Confirm
        open={confirm === "break"}
        title="커플 연결을 해제할까요?"
        desc={
          <>
            두 사람 모두 커플이 없는 상태가 되고, <b>함께 쓴 기록은 더 이상 볼 수 없어요.</b> 필요하면 먼저 데이터를 내보내 주세요.
          </>
        }
        confirmText="해제"
        danger
        loading={busy}
        onConfirm={danger}
        onClose={() => setConfirm(null)}
      />
      <Confirm
        open={confirm === "withdraw"}
        title="정말 탈퇴할까요?"
        desc={
          <>
            커플 연결이 해제되고, 내가 쓴 기록·사진·반응·댓글·메모와 계정이 <b>모두 삭제돼요.</b> 되돌릴 수 없어요.
          </>
        }
        confirmText="탈퇴"
        danger
        loading={busy}
        onConfirm={danger}
        onClose={() => setConfirm(null)}
      />
    </>
  );
}

function StatRow({
  label,
  pick,
  unit,
  stats,
  highlight,
}: {
  label: string;
  pick: (s: MemberStats) => number;
  unit: string;
  stats: CoupleStats;
  highlight?: boolean;
}) {
  const cls = `w-16 text-center font-semibold ${highlight ? "text-brand-strong" : ""}`;
  return (
    <>
      <span className="text-sub">{label}</span>
      <span className={cls}>
        {pick(stats.me)}
        <span className="text-xs font-normal text-sub">{unit}</span>
      </span>
      <span className={cls}>
        {pick(stats.partner)}
        <span className="text-xs font-normal text-sub">{unit}</span>
      </span>
    </>
  );
}

function MenuRow({ label, desc, onClick, danger, right }: { label: string; desc?: string; onClick: () => void; danger?: boolean; right?: React.ReactNode }) {
  return (
    <button onClick={onClick} className="flex w-full items-center gap-3 rounded-2xl px-3 py-3.5 text-left hover:bg-black/5">
      <span className="min-w-0 flex-1">
        <span className={`block font-medium ${danger ? "text-[#d6404e]" : ""}`}>{label}</span>
        {desc && <span className="block text-xs text-sub">{desc}</span>}
      </span>
      {right ?? <ChevronRight className="h-4 w-4 text-sub" />}
    </button>
  );
}

function NicknameSheet({ open, current, onClose, onSaved }: { open: boolean; current: string; onClose: () => void; onSaved: () => void }) {
  const toast = useToast();
  const [value, setValue] = useState(current);
  const [saving, setSaving] = useState(false);
  const [wasOpen, setWasOpen] = useState(false);
  if (open !== wasOpen) {
    setWasOpen(open);
    if (open) setValue(current);
  }
  const save = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      await api("/users/me", { method: "PATCH", body: { nickname: value.trim(), profileImageUrl: null } });
      onSaved();
    } catch (err) {
      toast(errorMessage(err));
    } finally {
      setSaving(false);
    }
  };
  return (
    <Sheet open={open} onClose={onClose} title="닉네임 변경">
      <form onSubmit={save} className="flex flex-col gap-4">
        <Field label="닉네임" hint={`${value.length}/20`}>
          <input className={inputClass} value={value} onChange={(e) => setValue(e.target.value.slice(0, 20))} autoFocus />
        </Field>
        <Button type="submit" full loading={saving} disabled={!value.trim() || value.trim() === current}>
          저장
        </Button>
      </form>
    </Sheet>
  );
}

function StartDateSheet({ open, current, onClose, onSaved }: { open: boolean; current: string; onClose: () => void; onSaved: () => void }) {
  const toast = useToast();
  const [value, setValue] = useState(current);
  const [saving, setSaving] = useState(false);
  const [wasOpen, setWasOpen] = useState(false);
  if (open !== wasOpen) {
    setWasOpen(open);
    if (open) setValue(current);
  }
  const save = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      await api("/couples/me", { method: "PATCH", body: { startedAt: value } });
      onSaved();
    } catch (err) {
      toast(errorMessage(err));
    } finally {
      setSaving(false);
    }
  };
  return (
    <Sheet open={open} onClose={onClose} title="사귄 날짜">
      <form onSubmit={save} className="flex flex-col gap-4">
        <Field label="처음 만난 날">
          <DateField label="처음 만난 날" value={value} max={todayKst()} onChange={setValue} />
        </Field>
        <Button type="submit" full loading={saving} disabled={value === current}>
          저장
        </Button>
      </form>
    </Sheet>
  );
}
