import { unregisterPush } from "./push";

const TOKEN_KEY = "ourtoday.token";
const REFRESH_KEY = "ourtoday.refresh";
const RETURN_KEY = "ourtoday.returnTo";

function safe<T>(fn: () => T, fallback: T): T {
  try {
    return fn();
  } catch {
    return fallback;
  }
}

export const getToken = () => safe(() => localStorage.getItem(TOKEN_KEY), null);
export const getRefreshToken = () => safe(() => localStorage.getItem(REFRESH_KEY), null);

export function setTokens(access: string, refresh?: string | null) {
  safe(() => {
    localStorage.setItem(TOKEN_KEY, access);
    if (refresh) localStorage.setItem(REFRESH_KEY, refresh);
  }, undefined);
}

export const clearToken = () =>
  safe(() => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(REFRESH_KEY);
  }, undefined);

/** 로그인 후 돌아갈 경로 (공유 링크로 들어온 경우 등). 같은 사이트 경로만 허용 */
export function rememberReturnTo(path: string) {
  if (path.startsWith("/") && !path.startsWith("//") && !path.startsWith("/login")) {
    safe(() => localStorage.setItem(RETURN_KEY, path), undefined);
  }
}

export function takeReturnTo(): string | null {
  return safe(() => {
    const v = localStorage.getItem(RETURN_KEY);
    localStorage.removeItem(RETURN_KEY);
    return v;
  }, null);
}

export async function logout() {
  // 이 기기로 푸시가 가지 않게 토큰 해제 (최대 1.5초, 실패해도 로그아웃은 진행)
  try {
    await Promise.race([unregisterPush(), new Promise((r) => setTimeout(r, 1500))]);
  } catch {
    // 무시
  }
  clearToken();
  window.location.replace("/login");
}
