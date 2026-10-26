import { clearToken, getRefreshToken, getToken, rememberReturnTo, setTokens } from "./auth";

export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public details?: Record<string, string>,
  ) {
    super(message);
  }
}

type Options = Omit<RequestInit, "body"> & { body?: unknown };

/** 백엔드 /api/v1 호출. 같은 도메인(Next rewrite)이라 CORS 없음 */
export async function api<T>(path: string, { body, headers, ...init }: Options = {}): Promise<T> {
  const send = () => {
    const token = getToken();
    return fetch(`/api/v1${path}`, {
      ...init,
      headers: {
        ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...headers,
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  };

  let res = await send();
  // 액세스 토큰(30분) 만료 → 리프레시 토큰으로 한 번 재발급 후 재시도
  if (res.status === 401 && getRefreshToken()) {
    const refreshed = await refreshTokens();
    if (refreshed === "ok") res = await send();
    // 망 불안정·서버 재시작 중이면 로그아웃시키지 않고 오류만 (리프레시 토큰 유지)
    else if (refreshed === "error") throw new ApiError(0, "NETWORK", "연결이 불안정해요. 잠시 후 다시 시도해 주세요.");
  }

  if (res.status === 204) return undefined as T;
  const text = await res.text();
  const data = text ? safeJson(text) : null;

  if (!res.ok) {
    const err = (data as { error?: { code: string; message: string; details?: Record<string, string> } })?.error;
    const error = new ApiError(res.status, err?.code ?? "UNKNOWN", err?.message ?? "잠시 후 다시 시도해 주세요.", err?.details);
    handleGlobal(error);
    throw error;
  }
  return data as T;
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

/** 인증/온보딩 오류는 어느 화면에서 나든 해당 단계로 보냄 */
function handleGlobal(error: ApiError) {
  if (typeof window === "undefined") return;
  const here = window.location.pathname + window.location.search;
  if (error.status === 401 || (error.status === 404 && error.message.includes("사용자를 찾을 수 없습니다"))) {
    clearToken();
    rememberReturnTo(here);
    window.location.replace("/login");
  } else if (error.code === "PROFILE_REQUIRED" && !here.startsWith("/onboarding")) {
    window.location.replace("/onboarding/profile");
  } else if (error.code === "COUPLE_REQUIRED" && !here.startsWith("/onboarding")) {
    window.location.replace("/onboarding/couple");
  }
}

let refreshing: Promise<"ok" | "invalid" | "error"> | null = null;

/** 동시에 여러 요청이 401 이어도 재발급은 한 번만. invalid = 리프레시 토큰 만료/무효 */
function refreshTokens(): Promise<"ok" | "invalid" | "error"> {
  refreshing ??= (async () => {
    try {
      const res = await fetch("/api/v1/auth/refresh", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken: getRefreshToken() }),
      });
      if (res.status === 401 || res.status === 400) return "invalid";
      if (!res.ok) return "error";
      const data = (await res.json()) as { accessToken: string; refreshToken: string };
      setTokens(data.accessToken, data.refreshToken);
      return "ok";
    } catch {
      return "error";
    } finally {
      setTimeout(() => (refreshing = null), 0);
    }
  })();
  return refreshing;
}

export const fetcher = <T,>(path: string) => api<T>(path);

export function errorMessage(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.details) {
      const first = Object.values(e.details)[0];
      if (first) return `${e.message} (${first})`;
    }
    return e.message;
  }
  return "네트워크 상태를 확인해 주세요.";
}
