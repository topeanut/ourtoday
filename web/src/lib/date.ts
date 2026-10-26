const ZONE = "Asia/Seoul";
const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

/** 한국 기준 오늘 YYYY-MM-DD */
export function todayKst(): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: ZONE }).format(new Date());
}

export function parseDate(ymd: string): Date {
  const [y, m, d] = ymd.split("-").map(Number);
  return new Date(y, m - 1, d);
}

export function toYmd(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

export function addDays(ymd: string, days: number): string {
  const d = parseDate(ymd);
  d.setDate(d.getDate() + days);
  return toYmd(d);
}

/** "10월 26일 (일)" — 올해가 아니면 연도 포함 */
export function formatDay(ymd: string, withYear = false): string {
  const d = parseDate(ymd);
  const sameYear = d.getFullYear() === parseDate(todayKst()).getFullYear();
  const head = withYear || !sameYear ? `${d.getFullYear()}년 ` : "";
  return `${head}${d.getMonth() + 1}월 ${d.getDate()}일 (${WEEKDAYS[d.getDay()]})`;
}

export function relativeTime(iso: string): string {
  const diff = (Date.now() - new Date(iso).getTime()) / 1000;
  if (diff < 60) return "방금";
  if (diff < 3600) return `${Math.floor(diff / 60)}분 전`;
  if (diff < 86400) return `${Math.floor(diff / 3600)}시간 전`;
  if (diff < 86400 * 7) return `${Math.floor(diff / 86400)}일 전`;
  return new Intl.DateTimeFormat("ko-KR", { timeZone: ZONE, month: "long", day: "numeric" }).format(new Date(iso));
}

export const weekdayLabels = WEEKDAYS;
