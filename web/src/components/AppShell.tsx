"use client";

import Link from "next/link";
import { useEffect } from "react";
import { usePathname } from "next/navigation";
import { markNavigation } from "@/lib/nav";
import { formatDay } from "@/lib/date";
import { BucketIcon, CalendarIcon, HomeIcon, UserIcon } from "./Icons";
import { useSession } from "./Session";
import { Avatar } from "./ui";

const TABS = [
  { href: "/", label: "홈", Icon: HomeIcon },
  { href: "/bucket", label: "버킷", Icon: BucketIcon },
  { href: "/calendar", label: "캘린더", Icon: CalendarIcon },
  { href: "/me", label: "마이", Icon: UserIcon },
];

function isActive(pathname: string, href: string) {
  return href === "/" ? pathname === "/" || pathname.startsWith("/posts") : pathname.startsWith(href);
}

/** 모바일: 하단 탭바 / PC(md 이상): 좌측 사이드바 */
export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const { me, partner, couple } = useSession();
  const hideTabs = /^\/posts\/(new|[^/]+\/edit)/.test(pathname);

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
          {TABS.map(({ href, label, Icon }) => {
            const active = isActive(pathname, href);
            return (
              <Link
                key={href}
                href={href}
                className={`flex items-center gap-3 rounded-2xl px-3 py-3 font-medium transition ${
                  active ? "bg-brand-soft text-brand-strong" : "text-sub hover:bg-black/5"
                }`}
              >
                <Icon className="h-5 w-5" />
                {label === "마이" ? "마이페이지" : label}
              </Link>
            );
          })}
        </nav>
        <Link href="/posts/new" className="mt-6 rounded-2xl bg-brand py-3 text-center font-semibold text-white hover:bg-brand-strong">
          오늘 기록하기
        </Link>
        <div className="mt-auto flex items-center gap-3 rounded-2xl bg-white p-3">
          <div className="flex -space-x-2">
            <Avatar name={me.nickname} seed={me.id} size={32} />
            {partner && <Avatar name={partner.nickname} seed={partner.id} size={32} />}
          </div>
          <div className="min-w-0 text-sm">
            <p className="truncate font-semibold">
              {me.nickname} · {partner?.nickname}
            </p>
            <p className="text-xs text-sub">{formatDay(couple.startedAt, true)}부터</p>
          </div>
        </div>
      </aside>

      <main className={`min-w-0 flex-1 ${hideTabs ? "" : "pb-[calc(76px+var(--safe-bottom))]"} md:pb-0`}>{children}</main>

      {!hideTabs && (
        <nav className="pb-safe fixed inset-x-0 bottom-0 z-40 border-t border-line bg-white/95 backdrop-blur md:hidden">
          <div className="mx-auto grid max-w-md grid-cols-4">
            {TABS.map(({ href, label, Icon }) => {
              const active = isActive(pathname, href);
              return (
                <Link
                  key={href}
                  href={href}
                  className={`flex h-[60px] flex-col items-center justify-center gap-1 text-[11px] font-medium ${
                    active ? "text-brand-strong" : "text-sub"
                  }`}
                  aria-current={active ? "page" : undefined}
                >
                  <Icon className="h-6 w-6" />
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
