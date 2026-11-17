"use client";

import { useEffect, useRef } from "react";
import { useKeyboardViewport } from "@/lib/useKeyboardViewport";
import { CloseIcon } from "./Icons";

const PALETTE = ["#ff7a8a", "#ffa36c", "#f6c445", "#7cc69a", "#6fb3e8", "#a68ee8"];

/** 닉네임 첫 글자 아바타 (사진 업로드 대신) */
export function Avatar({ name, seed, size = 36 }: { name: string; seed?: string; size?: number }) {
  const key = seed ?? name;
  let h = 0;
  for (let i = 0; i < key.length; i++) h = (h * 31 + key.charCodeAt(i)) >>> 0;
  return (
    <span
      className="inline-flex shrink-0 items-center justify-center rounded-full font-semibold text-white"
      style={{ width: size, height: size, background: PALETTE[h % PALETTE.length], fontSize: size * 0.42 }}
      aria-hidden
    >
      {name.trim().charAt(0) || "?"}
    </span>
  );
}

export function Spinner({ className = "" }: { className?: string }) {
  return (
    <span
      className={`inline-block h-5 w-5 animate-spin rounded-full border-2 border-brand/30 border-t-brand ${className}`}
      role="status"
      aria-label="불러오는 중"
    />
  );
}

export function PageSpinner() {
  return (
    <div className="flex min-h-[40vh] items-center justify-center">
      <Spinner className="h-7 w-7" />
    </div>
  );
}

export function Empty({ title, desc, action }: { title: string; desc?: string; action?: React.ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-2 rounded-3xl border border-dashed border-line bg-card/60 px-6 py-12 text-center">
      <p className="font-semibold">{title}</p>
      {desc && <p className="text-sm text-sub">{desc}</p>}
      {action && <div className="mt-3">{action}</div>}
    </div>
  );
}

type ButtonProps = React.ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: "primary" | "ghost" | "soft" | "danger";
  loading?: boolean;
  full?: boolean;
};

export function Button({ variant = "primary", loading, full, className = "", children, disabled, ...rest }: ButtonProps) {
  const styles = {
    primary: "bg-brand text-white hover:bg-brand-strong disabled:bg-brand/40",
    soft: "bg-brand-soft text-brand-strong hover:bg-brand-soft/70 disabled:opacity-50",
    ghost: "bg-transparent text-ink hover:bg-black/5 disabled:opacity-40",
    danger: "bg-[#fdecec] text-[#d6404e] hover:bg-[#fbdcdc] disabled:opacity-50",
  }[variant];
  return (
    <button
      className={`inline-flex h-12 items-center justify-center gap-2 rounded-2xl px-5 text-[15px] font-semibold transition active:scale-[0.98] ${styles} ${full ? "w-full" : ""} ${className}`}
      disabled={disabled || loading}
      {...rest}
    >
      {loading ? <Spinner className="h-4 w-4 border-current/30 border-t-current" /> : children}
    </button>
  );
}

export function Field({ label, hint, children }: { label: string; hint?: React.ReactNode; children: React.ReactNode }) {
  return (
    <label className="block">
      <span className="mb-1.5 flex items-baseline justify-between text-sm font-medium text-sub">
        {label}
        {hint && <span className="text-xs font-normal">{hint}</span>}
      </span>
      {children}
    </label>
  );
}

export const inputClass =
  "w-full rounded-2xl border border-line bg-white px-4 py-3 outline-none transition placeholder:text-sub/60 focus:border-brand focus:ring-4 focus:ring-brand/15";

/**
 * 시트 안 저장 버튼 자리. 입력칸이 많은 시트는 키보드가 열리면 보이는 높이에 다 안 들어가서
 * 버튼이 아래로 숨으므로, 시트 아래쪽에 붙여 두고 입력칸만 시트 안에서 스크롤되게 함
 */
export function SheetActions({ children }: { children: React.ReactNode }) {
  return <div className="sticky -bottom-px z-10 -mx-5 flex flex-col gap-2 bg-white px-5 pb-1 pt-2">{children}</div>;
}

/** 모바일 하단 시트 / PC 가운데 모달 */
export function Sheet({
  open,
  onClose,
  title,
  children,
}: {
  open: boolean;
  onClose: () => void;
  title?: string;
  children: React.ReactNode;
}) {
  const ref = useRef<HTMLDivElement>(null);
  // iOS 는 키보드가 열려도 화면 높이를 줄이지 않아서, 화면 아래에 붙은 시트의 버튼이 키보드에 가려짐 → 보이는 영역에 맞춤
  useKeyboardViewport(ref, open);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    document.addEventListener("keydown", onKey);
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = prev;
    };
  }, [open, onClose]);

  if (!open) return null;
  return (
    <div
      ref={ref}
      className="group fixed inset-x-0 top-0 z-50 flex h-dvh items-end justify-center md:items-center"
      role="dialog"
      aria-modal
      aria-label={title}
    >
      <button className="animate-fade absolute inset-0 bg-black/35" onClick={onClose} aria-label="닫기" />
      <div className="animate-sheet relative max-h-[88%] w-full overflow-y-auto overscroll-contain rounded-t-3xl bg-white px-5 pb-[calc(20px+var(--safe-bottom))] pt-4 shadow-xl group-data-[keyboard=open]:max-h-[calc(100%-12px)] group-data-[keyboard=open]:pb-4 md:max-w-md md:rounded-3xl md:pb-6">
        <div className="mx-auto mb-3 h-1 w-10 rounded-full bg-line md:hidden" />
        {title && (
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-lg font-bold">{title}</h2>
            <button onClick={onClose} className="-mr-2 rounded-full p-2 text-sub hover:bg-black/5" aria-label="닫기">
              <CloseIcon className="h-5 w-5" />
            </button>
          </div>
        )}
        {children}
      </div>
    </div>
  );
}

export function Confirm({
  open,
  title,
  desc,
  confirmText = "확인",
  danger,
  loading,
  onConfirm,
  onClose,
}: {
  open: boolean;
  title: string;
  desc?: React.ReactNode;
  confirmText?: string;
  danger?: boolean;
  loading?: boolean;
  onConfirm: () => void;
  onClose: () => void;
}) {
  return (
    <Sheet open={open} onClose={onClose}>
      <h2 className="text-lg font-bold">{title}</h2>
      {desc && <div className="mt-2 text-[15px] leading-relaxed text-sub">{desc}</div>}
      <div className="mt-6 grid grid-cols-2 gap-2">
        <Button variant="ghost" className="bg-black/5" onClick={onClose}>
          취소
        </Button>
        <Button variant={danger ? "danger" : "primary"} loading={loading} onClick={onConfirm}>
          {confirmText}
        </Button>
      </div>
    </Sheet>
  );
}

export function Card({ className = "", children }: { className?: string; children: React.ReactNode }) {
  return <section className={`rounded-3xl bg-card p-5 shadow-[0_1px_2px_rgba(43,35,38,0.04),0_8px_24px_rgba(43,35,38,0.04)] ${className}`}>{children}</section>;
}

export function Toggle({ checked, onChange, label }: { checked: boolean; onChange: (v: boolean) => void; label: string }) {
  return (
    <button
      role="switch"
      aria-checked={checked}
      aria-label={label}
      onClick={() => onChange(!checked)}
      className={`relative h-7 w-12 shrink-0 rounded-full transition ${checked ? "bg-brand" : "bg-line"}`}
    >
      <span className={`absolute top-0.5 h-6 w-6 rounded-full bg-white shadow transition ${checked ? "left-[22px]" : "left-0.5"}`} />
    </button>
  );
}
