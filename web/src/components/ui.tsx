"use client";

import { useEffect, useRef, useState } from "react";
import { useKeyboardViewport } from "@/lib/useKeyboardViewport";
import { formatDay } from "@/lib/date";
import { ChevronDown, CloseIcon } from "./Icons";

const PALETTE = ["#ff7a8a", "#ffa36c", "#f6c445", "#7cc69a", "#6fb3e8", "#a68ee8"];

/**
 * 프로필 사진 아바타. 사진이 없거나(카카오·직접 업로드 모두 없음) 못 불러오면 닉네임 첫 글자로 표시
 */
export function Avatar({ name, seed, src, size = 36 }: { name: string; seed?: string; src?: string | null; size?: number }) {
  // 만료·삭제된 링크면 첫 글자로 (src 가 바뀌면 다시 시도)
  const [failedSrc, setFailedSrc] = useState<string | null>(null);
  if (src && src !== failedSrc) {
    return (
      <img
        src={src}
        alt=""
        aria-hidden
        onError={() => setFailedSrc(src)}
        className="inline-block shrink-0 rounded-full bg-line object-cover"
        style={{ width: size, height: size }}
      />
    );
  }
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

const DOT_SIZE = { sm: "h-1.5 w-1.5", md: "h-2 w-2", lg: "h-2.5 w-2.5" };

/** 점 3개 로더. tone="current" 면 부모 글자색을 따라감 (버튼 안 등) */
export function Spinner({
  size = "md",
  tone = "brand",
  className = "",
}: {
  size?: keyof typeof DOT_SIZE;
  tone?: "brand" | "current";
  className?: string;
}) {
  return (
    <span
      className={`inline-flex items-center gap-1 ${tone === "brand" ? "text-brand" : ""} ${className}`}
      role="status"
      aria-label="불러오는 중"
    >
      {[0, 1, 2].map((i) => (
        <span key={i} className={`animate-dot rounded-full bg-current ${DOT_SIZE[size]}`} style={{ animationDelay: `${i * 0.16}s` }} />
      ))}
    </span>
  );
}

export function PageSpinner() {
  return (
    <div className="animate-delayed flex min-h-[40vh] items-center justify-center">
      <Spinner size="lg" />
    </div>
  );
}

export function Empty({ title, desc, action }: { title: string; desc?: string; action?: React.ReactNode }) {
  return (
    <div className="animate-fade flex flex-col items-center gap-2 rounded-3xl border border-dashed border-line bg-card/60 px-6 py-12 text-center">
      <p className="font-semibold">{title}</p>
      {desc && <p className="text-sm text-sub">{desc}</p>}
      {action && <div className="mt-3">{action}</div>}
    </div>
  );
}

/**
 * 누름 효과. 모든 버튼·버튼처럼 생긴 링크는 이 중 하나를 씀
 *  - scale: 작은 버튼 (살짝 작아짐)
 *  - soft: 카드처럼 큰 버튼 (크게 줄면 어색해서 아주 조금 + 살짝 흐려짐)
 *  - dim: 줄 전체·투명 버튼 (크기는 그대로, 배경만 어둡게)
 *  - none: 시트 뒤 배경처럼 효과가 없어야 하는 것
 */
export const PRESS = {
  scale: "transition active:scale-[0.96] disabled:active:scale-100",
  soft: "transition active:scale-[0.985] active:opacity-90 disabled:active:scale-100",
  dim: "transition active:bg-black/[0.06]",
  none: "",
} as const;
export type PressKind = keyof typeof PRESS;

type PressableProps = React.ButtonHTMLAttributes<HTMLButtonElement> & { press?: PressKind };

/** 모든 버튼의 기본. 기본 type 은 "button" (폼 안에서 실수로 제출되지 않게) */
export function Pressable({ press = "scale", className = "", type = "button", ...rest }: PressableProps) {
  return <button type={type} className={`${PRESS[press]} disabled:cursor-not-allowed ${className}`} {...rest} />;
}

/** 아이콘만 있는 둥근 버튼. 손가락으로 누르기 쉽게 44px, 화면 읽기용 이름 필수 */
export function IconButton({
  label,
  tone = "plain",
  className = "",
  ...rest
}: Omit<PressableProps, "aria-label"> & { label: string; tone?: "plain" | "sub" | "brand" | "overlay" }) {
  const tones = {
    plain: "text-ink hover:bg-black/5 active:bg-black/10",
    sub: "text-sub hover:bg-black/5 active:bg-black/10",
    brand: "bg-brand text-white hover:bg-brand-strong",
    overlay: "bg-white/15 text-white active:bg-white/25",
  }[tone];
  return (
    <Pressable
      aria-label={label}
      className={`inline-flex h-11 w-11 shrink-0 items-center justify-center rounded-full ${tones} ${className}`}
      {...rest}
    />
  );
}

/** 시트 안 메뉴 줄 (수정하기 / 삭제하기 등) */
export function MenuItem({
  icon,
  danger,
  children,
  className = "",
  ...rest
}: PressableProps & { icon?: React.ReactNode; danger?: boolean }) {
  return (
    <Pressable
      press="dim"
      className={`flex items-center gap-3 rounded-2xl px-3 py-4 text-left font-medium hover:bg-black/5 ${danger ? "text-[#d6404e]" : ""} ${className}`}
      {...rest}
    >
      {icon}
      {children}
    </Pressable>
  );
}

type ButtonProps = React.ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: "primary" | "ghost" | "soft" | "danger";
  /** 높이·글자 크기는 className 으로 덮으면 h-12 와 충돌해 무시되므로 size 로 고름 */
  size?: "lg" | "md" | "sm";
  loading?: boolean;
  full?: boolean;
};

const BUTTON_SIZE = {
  lg: "h-12 px-5 text-[15px]",
  md: "h-11 px-4 text-sm",
  sm: "h-10 px-4 text-sm",
};

export function Button({ variant = "primary", size = "lg", loading, full, className = "", children, disabled, ...rest }: ButtonProps) {
  const styles = {
    primary: "bg-brand text-white hover:bg-brand-strong active:bg-brand-strong disabled:bg-brand/40",
    soft: "bg-brand-soft text-brand-strong hover:bg-brand-soft/70 disabled:opacity-50",
    ghost: "bg-transparent text-ink hover:bg-black/5 active:bg-black/10 disabled:opacity-40",
    danger: "bg-[#fdecec] text-[#d6404e] hover:bg-[#fbdcdc] active:bg-[#fbdcdc] disabled:opacity-50",
  }[variant];
  return (
    <Pressable
      className={`inline-flex items-center justify-center gap-2 rounded-2xl font-semibold ${BUTTON_SIZE[size]} ${styles} ${full ? "w-full" : ""} ${className}`}
      disabled={disabled || loading}
      {...rest}
    >
      {loading ? <Spinner size="sm" tone="current" /> : children}
    </Pressable>
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
  "w-full rounded-2xl border border-line bg-white px-4 py-3 outline-none transition placeholder:text-sub focus:border-brand focus:ring-4 focus:ring-brand/15";

/**
 * 날짜 입력. 브라우저 기본 date 입력은 기기마다 글자 형식("09/11/2025", "2025. 9. 11.")과 화살표 위치가 달라서
 * 보이는 부분은 직접 그리고("2025년 9월 11일 (목)" + 오른쪽 화살표), 투명한 기본 입력을 위에 덮어 누르면 기기 달력이 열리게 함
 * @param variant field: 테두리 있는 입력칸 / inline: 줄 안에서 오른쪽 정렬(글쓰기 날짜 줄)
 */
export function DateField({
  value,
  onChange,
  min,
  max,
  placeholder = "날짜 선택",
  clearable = false,
  variant = "field",
  label,
}: {
  value: string;
  onChange: (value: string) => void;
  min?: string;
  max?: string;
  placeholder?: string;
  clearable?: boolean;
  variant?: "field" | "inline";
  label?: string;
}) {
  const ref = useRef<HTMLInputElement>(null);
  // PC 브라우저는 입력칸 글자 부분을 눌러도 달력이 안 열려서 직접 엶 (휴대폰은 누르면 바로 열림)
  const openPicker = () => {
    try {
      ref.current?.showPicker?.();
    } catch {
      // showPicker 미지원·제한 → 기본 동작
    }
  };
  const box =
    variant === "field"
      ? "w-full rounded-2xl border border-line bg-white px-4 py-3 transition focus-within:border-brand focus-within:ring-4 focus-within:ring-brand/15"
      : "rounded-xl py-2 pl-2 focus-within:bg-brand-soft/60";
  return (
    <div className={`relative flex items-center gap-2 ${box}`}>
      <span className={`${variant === "inline" ? "ml-auto" : ""} ${value ? "text-ink" : "text-sub"} tabular-nums`}>
        {value ? formatDay(value, true) : placeholder}
      </span>
      <input
        ref={ref}
        type="date"
        aria-label={label}
        value={value}
        min={min}
        max={max}
        onClick={openPicker}
        onChange={(e) => (e.target.value || clearable) && onChange(e.target.value)}
        className="absolute inset-0 h-full w-full cursor-pointer appearance-none opacity-0"
      />
      {clearable && value && (
        <Pressable
          press="dim"
          onClick={() => onChange("")}
          className="relative z-10 -my-2 ml-auto rounded-lg px-2.5 py-2 text-sm text-sub"
        >
          지우기
        </Pressable>
      )}
      <ChevronDown
        className={`h-5 w-5 shrink-0 text-sub ${variant === "field" && !(clearable && value) ? "ml-auto" : ""}`}
      />
    </div>
  );
}

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
      <Pressable press="none" className="animate-fade absolute inset-0 cursor-default bg-black/35" onClick={onClose} aria-label="닫기" />
      <div className="animate-sheet relative max-h-[88%] w-full overflow-y-auto overscroll-contain rounded-t-3xl bg-white px-5 pb-[calc(20px+var(--safe-bottom))] pt-4 shadow-xl group-data-[keyboard=open]:max-h-[calc(100%-12px)] group-data-[keyboard=open]:pb-4 md:max-w-md md:rounded-3xl md:pb-6">
        <div className="mx-auto mb-3 h-1 w-10 rounded-full bg-line md:hidden" />
        {title && (
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-lg font-bold">{title}</h2>
            <IconButton onClick={onClose} tone="sub" label="닫기" className="-mr-2.5">
              <CloseIcon className="h-5 w-5" />
            </IconButton>
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
    <Pressable
      role="switch"
      aria-checked={checked}
      aria-label={label}
      onClick={() => onChange(!checked)}
      className={`relative h-7 w-12 shrink-0 rounded-full ${checked ? "bg-brand" : "bg-line"}`}
    >
      <span className={`absolute top-0.5 h-6 w-6 rounded-full bg-white shadow transition ${checked ? "left-[22px]" : "left-0.5"}`} />
    </Pressable>
  );
}
