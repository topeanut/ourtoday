/**
 * 푸시 알림 브리지 + 서버 토큰 등록
 *  - 웹 → 앱: pushStatus / pushPermission / openNotificationSettings
 *  - 앱 → 웹: window 이벤트 "ourtoday:push" { permission, token, platform }
 * 옛 앱(1.0/1.0.1)은 응답 이벤트가 없으므로 모든 함수가 타임아웃으로 조용히 끝남 (서버 요청 없음)
 */
import { api } from "./api";
import { postNative } from "./native";

export type PushPermission = "granted" | "denied" | "default";
export type PushState = {
  supported: boolean;
  permission: PushPermission;
  token: string | null;
  platform: "android" | "ios" | null;
};

const EVENT = "ourtoday:push";
const STORAGE_KEY = "ourtoday.pushToken";
const UNSUPPORTED: PushState = { supported: false, permission: "default", token: null, platform: null };

function readStored(): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

function writeStored(token: string | null) {
  try {
    if (token) localStorage.setItem(STORAGE_KEY, token);
    else localStorage.removeItem(STORAGE_KEY);
  } catch {
    // 저장 못 해도 동작에는 영향 없음
  }
}

function toState(detail: unknown): PushState {
  const d = (detail ?? {}) as { permission?: PushPermission; token?: string | null; platform?: string };
  return {
    supported: true,
    permission: d.permission === "granted" || d.permission === "denied" ? d.permission : "default",
    token: d.token || null,
    platform: d.platform === "android" || d.platform === "ios" ? d.platform : null,
  };
}

/** 메시지를 보내고 ourtoday:push 응답을 기다림. 응답이 없으면 UNSUPPORTED */
function ask(type: "pushStatus" | "pushPermission", timeoutMs: number): Promise<PushState> {
  return new Promise((resolve) => {
    if (typeof window === "undefined") return resolve(UNSUPPORTED);
    const onResult = (e: Event) => {
      clearTimeout(timer);
      window.removeEventListener(EVENT, onResult);
      resolve(toState((e as CustomEvent).detail));
    };
    window.addEventListener(EVENT, onResult);
    const timer = setTimeout(() => {
      window.removeEventListener(EVENT, onResult);
      resolve(UNSUPPORTED);
    }, timeoutMs);
    if (!postNative({ type })) {
      clearTimeout(timer);
      window.removeEventListener(EVENT, onResult);
      resolve(UNSUPPORTED);
    }
  });
}

export function getPushState(timeoutMs = 1500): Promise<PushState> {
  return ask("pushStatus", timeoutMs);
}

/** 같은 토큰 등록이 동시에 여러 번 나가지 않게 진행 중인 요청을 공유 */
const pending = new Map<string, Promise<void>>();
/** 이 페이지 세션에서 서버 등록을 마친 토큰. 새로 실행하면 비어 있어 한 번은 다시 등록(계정 이동 반영) */
let registered: string | null = null;

function register(state: PushState): Promise<void> {
  const { token, platform } = state;
  if (state.permission !== "granted" || !token || !platform) return Promise.resolve();
  if (token === registered && token === readStored()) return Promise.resolve();
  const inFlight = pending.get(token);
  if (inFlight) return inFlight;
  const p = (async () => {
    try {
      await api("/push-tokens", { method: "POST", body: { token, platform } });
      writeStored(token);
      registered = token;
    } catch {
      // 다음 실행 때 다시 시도
    } finally {
      pending.delete(token);
    }
  })();
  pending.set(token, p);
  return p;
}

/** 시스템 권한 요청 (사용자가 대화상자에 답할 시간이 있어 길게 기다림). 허용되면 서버 등록까지 */
export async function requestPushPermission(): Promise<PushState> {
  const state = await ask("pushPermission", 120_000);
  await register(state);
  return state;
}

let listening = false;

/** 로그인 상태에서 호출: 허용된 토큰을 서버에 등록하고, 토큰이 바뀌면 다시 등록 */
export async function syncPushToken(): Promise<void> {
  if (typeof window === "undefined") return;
  if (!listening) {
    listening = true;
    window.addEventListener(EVENT, (e) => {
      const s = toState((e as CustomEvent).detail);
      if (s.token && s.token !== readStored()) void register(s);
    });
  }
  await register(await getPushState());
}

/** 로그아웃 직전: 이 기기 토큰을 서버에서 지움. 실패해도 무시 */
export async function unregisterPush(): Promise<void> {
  const token = readStored();
  if (!token) return;
  try {
    await api("/push-tokens", { method: "DELETE", body: { token } });
  } catch {
    // 무시
  }
  writeStored(null);
  registered = null;
}

export function openNotificationSettings(): void {
  postNative({ type: "openNotificationSettings" });
}
