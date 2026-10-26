"use client";

import { createContext, useContext, useEffect } from "react";
import { usePathname, useRouter } from "next/navigation";
import useSWR from "swr";
import { fetcher } from "@/lib/api";
import { getToken, rememberReturnTo } from "@/lib/auth";
import type { Couple, CoupleMember, OnboardingStatus, User } from "@/lib/types";
import { PageSpinner } from "./ui";

interface Session {
  me: User;
  couple: Couple;
  partner: CoupleMember | undefined;
  refresh: () => void;
}

const SessionContext = createContext<Session | null>(null);

export function useSession(): Session {
  const s = useContext(SessionContext);
  if (!s) throw new Error("SessionGate 밖에서 사용");
  return s;
}

/** 로그인 + 커플 연결까지 끝난 사용자만 통과. 아니면 해당 단계 화면으로 */
export function SessionGate({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const hasToken = typeof window !== "undefined" && !!getToken();

  // 셋을 동시에 요청 (순서대로 기다리면 느린 서버에서 왕복이 쌓임).
  // 커플이 없으면 /couples/me 는 403 → 공통 처리에서 온보딩으로 보냄
  const status = useSWR<{ status: OnboardingStatus }>(hasToken ? "/users/me/onboarding-status" : null, fetcher);
  const me = useSWR<User>(hasToken ? "/users/me" : null, fetcher);
  const couple = useSWR<Couple>(hasToken ? "/couples/me" : null, fetcher, { shouldRetryOnError: false });
  const connected = status.data?.status === "CONNECTED";

  useEffect(() => {
    if (!getToken()) {
      rememberReturnTo(pathname + window.location.search);
      router.replace("/login");
      return;
    }
    if (status.isValidating) return;
    const s = status.data?.status;
    if (s === "NEED_PROFILE") router.replace("/onboarding/profile");
    else if (s === "NO_COUPLE" || s === "PENDING_INVITE") router.replace("/onboarding/couple");
  }, [status.data, status.isValidating, pathname, router]);

  if (!connected || !me.data || !couple.data) return <PageSpinner />;

  const value: Session = {
    me: me.data,
    couple: couple.data,
    partner: couple.data.members.find((m) => m.id !== me.data!.id),
    refresh: () => {
      me.mutate();
      couple.mutate();
    },
  };
  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}
