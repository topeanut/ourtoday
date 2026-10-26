import type { Metadata, Viewport } from "next";
import "./globals.css";
import { ToastProvider } from "@/components/Toast";
import { IS_DEV } from "@/lib/env";

export const metadata: Metadata = {
  title: { default: IS_DEV ? "[DEV] 오늘의 우리" : "오늘의 우리", template: "%s · 오늘의 우리" },
  robots: IS_DEV ? { index: false, follow: false } : undefined,
  description: "연인과 부담 없이 하루를 기록하고, 시간이 지나 다시 꺼내 보는 우리만의 일기장",
  applicationName: "오늘의 우리",
  appleWebApp: { capable: true, title: "오늘의 우리", statusBarStyle: "default" },
  manifest: "/manifest.webmanifest",
  icons: { icon: "/icon.svg", apple: "/icon.svg" },
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  viewportFit: "cover",
  themeColor: "#fff8f5",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ko">
      <head>
        <link
          rel="stylesheet"
          href="https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/dist/web/variable/pretendardvariable-dynamic-subset.min.css"
        />
      </head>
      <body className="antialiased">
        <ToastProvider>{children}</ToastProvider>
        {IS_DEV && (
          <div
            className="pointer-events-none fixed left-1/2 z-[80] -translate-x-1/2 rounded-b-lg bg-[#3b7fbf] px-2 py-0.5 text-[10px] font-bold text-white"
            style={{ top: "var(--safe-top)" }}
          >
            DEV
          </div>
        )}
      </body>
    </html>
  );
}
