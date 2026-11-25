"use client";

import Link from "next/link";
import { useEffect, useLayoutEffect, useState } from "react";
import { usePathname } from "next/navigation";
import { markNavigation } from "@/lib/nav";
import { formatDay } from "@/lib/date";
import { BucketIcon, CalendarIcon, HomeIcon, UserIcon } from "./Icons";
import { useSession } from "./Session";
import { Avatar, PRESS } from "./ui";

const TABS = [
  { href: "/", label: "홈", Icon: HomeIcon },
  { href: "/bucket", label: "버킷", Icon: BucketIcon },
  { href: "/calendar", label: "캘린더", Icon: CalendarIcon },
  { href: "/me", label: "마이", Icon: UserIcon },
];

function isActive(pathname: string, href: string) {
  return href === "/" ? pathname === "/" || pathname.startsWith("/posts") : pathname.startsWith(href);
}

/**
 * 화면 전환 모션
 * - 탭 버튼: 탭 순서대로 옆에서 밀려 들어옴 (pendingDir)
 * - 그 밖의 앞으로 이동(링크·router.push): 살짝 떠오르며 나타남
 * - 뒤로가기·iOS 스와이프·Android 백버튼(popstate): 모션 없음 — iOS 스와이프 모션과 겹치지 않게
 */
let pendingDir: 1 | -1 | 0 = 0;
let popNavigation = false;
function markTabClick(from: number, to: number) {
  pendingDir = to > from ? 1 : to < from ? -1 : 0;
}

function transitionClass() {
  if (pendingDir) return pendingDir > 0 ? "animate-tab-right" : "animate-tab-left";
  return popNavigation ? "" : "animate-page";
}

/** 모바일: 하단 탭바 / PC(md 이상): 좌측 사이드바 */
export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const { me, partner, couple } = useSession();
  const hideTabs = /^\/posts\/(new|[^/]+(\/edit)?\/?$)/.test(pathname);
  const tabIndex = Math.max(0, TABS.findIndex((t) => isActive(pathname, t.href)));
  // 첫 화면은 모션 없이
  const [transition, setTransition] = useState({ path: pathname, cls: "" });

  // popstate 는 Next 가 새 화면을 그리기 전에 먼저 발생하므로 여기서 뒤로가기 여부를 표시
  useEffect(() => {
    const onPop = () => {
      popNavigation = true;
    };
    window.addEventListener("popstate", onPop);
    return () => window.removeEventListener("popstate", onPop);
  }, []);

  // 그리기 전에 모션을 정해서 새 화면이 제자리에 한 번 보였다가 튀는 일 없게
  useLayoutEffect(() => {
    if (transition.path === pathname) return;
    setTransition({ path: pathname, cls: transitionClass() });
    pendingDir = 0;
    popNavigation = false;
  }, [pathname, transition.path]);
  const slideClass = transition.path === pathname ? transition.cls : "";

  useEffect(() => {
    markNavigation(pathname);
  }, [pathname]);

  return (
    <div className="mx-auto flex min-h-dvh max-w-6xl">
      <aside className="sticky top-0 hidden h-dvh w-64 shrink-0 flex-col border-r border-line px-5 py-8 md:flex">
        <Link href="/" className="mb-10 flex items-center gap-2 px-3">
          <img src="/icon.svg" alt="" className="h-9 w-9" />
          <span className="text-lg font-bold">오늘의 우리</span>
        </Link>
        <nav className="flex flex-col gap-1">
          {TABS.map(({ href, label, Icon }, i) => {
            const active = isActive(pathname, href);
            return (
              <Link
                key={href}
                href={href}
                onClick={() => markTabClick(tabIndex, i)}
                className={`flex items-center gap-3 rounded-2xl px-3 py-3 font-medium ${PRESS.scale} ${
                  active ? "bg-brand-soft text-brand-strong" : "text-sub hover:bg-black/5 active:bg-black/10"
                }`}
              >
                <Icon className="h-5 w-5" />
                {label === "마이" ? "마이페이지" : label}
              </Link>
            );
          })}
        </nav>
        <Link href="/posts/new" className={`mt-6 rounded-2xl bg-brand py-3 text-center font-semibold text-white hover:bg-brand-strong active:bg-brand-strong ${PRESS.scale}`}>
          오늘 기록하기
        </Link>
        <div className="mt-auto flex items-center gap-3 rounded-2xl bg-white p-3">
          <div className="flex -space-x-2">
            <Avatar name={me.nickname} seed={me.id} src={me.profileImageUrl} size={32} />
            {partner && <Avatar name={partner.nickname} seed={partner.id} src={partner.profileImageUrl} size={32} />}
          </div>
          <div className="min-w-0 text-sm">
            <p className="truncate font-semibold">
              {me.nickname} · {partner?.nickname}
            </p>
            <p className="text-xs text-sub">{formatDay(couple.startedAt, true)}부터</p>
          </div>
        </div>
      </aside>

      <main className={`min-w-0 flex-1 overflow-x-clip ${hideTabs ? "" : "pb-[calc(76px+var(--safe-bottom))]"} md:pb-0`}>
        <div key={pathname} className={slideClass}>
          {children}
        </div>
      </main>

      {!hideTabs && (
        <nav className="pb-safe fixed inset-x-0 bottom-0 z-40 border-t border-line bg-white/95 backdrop-blur md:hidden">
          <div className="mx-auto grid max-w-md grid-cols-4">
            {TABS.map(({ href, label, Icon }, i) => {
              const active = isActive(pathname, href);
              return (
                <Link
                  key={href}
                  href={href}
                  onClick={() => markTabClick(tabIndex, i)}
                  className={`group flex h-[60px] flex-col items-center justify-center gap-1 text-[11px] font-medium transition active:opacity-70 ${
                    active ? "text-brand-strong" : "text-sub"
                  }`}
                  aria-current={active ? "page" : undefined}
                >
                  {/* 탭 전체는 누르는 영역, 눌림은 아이콘이 살짝 작아지는 걸로 */}
                  <Icon className="h-6 w-6 transition group-active:scale-90" />
                  {label}
                </Link>
              );
            })}
          </div>
        </nav>
      )}
    </div>
  );
}

/** 각 화면 상단 제목줄 */
export function PageHeader({ title, left, right }: { title: React.ReactNode; left?: React.ReactNode; right?: React.ReactNode }) {
  return (
    <header className="pt-safe sticky top-0 z-30 bg-cream/90 backdrop-blur">
      <div className="mx-auto flex h-14 max-w-2xl items-center gap-2 px-4 md:h-16 md:px-8">
        {left}
        <h1 className="flex-1 truncate text-lg font-bold md:text-xl">{title}</h1>
        {right}
      </div>
    </header>
  );
}
