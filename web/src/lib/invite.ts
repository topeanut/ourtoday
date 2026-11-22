export const APP_STORE_URL = "https://apps.apple.com/kr/app/id6830693170";
export const PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=app.ourtoday";

const PENDING_KEY = "ourtoday.inviteCode";

/** 공유하는 초대 링크. 열면 기기에 맞는 설치 버튼과 초대코드가 나옴 (/invite) */
export function inviteUrl(code: string) {
  return `${window.location.origin}/invite?code=${encodeURIComponent(code)}`;
}

/** 초대 링크로 들어온 코드는 로그인·프로필 입력을 거쳐도 '초대코드 입력하기'에 채워지게 기억해 둠 */
export function rememberInviteCode(code: string) {
  try {
    localStorage.setItem(PENDING_KEY, code);
  } catch {
    // 저장 못 하면 직접 입력
  }
}

export function takeInviteCode(): string | null {
  try {
    const v = localStorage.getItem(PENDING_KEY);
    localStorage.removeItem(PENDING_KEY);
    return v;
  } catch {
    return null;
  }
}

/** 초대코드 형식(영문·숫자 6~8자)만 통과 */
export function normalizeInviteCode(raw: string | null | undefined): string {
  return (raw ?? "").replace(/[^0-9a-zA-Z]/g, "").slice(0, 8).toUpperCase();
}
