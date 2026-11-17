/**
 * Sign in with Apple
 *  - iOS 앱: 네이티브 Apple 로그인(브리지 appleLogin) → 결과를 window 이벤트로 받음
 *  - 웹: Apple JS SDK 팝업 (Services ID app.ourtoday.web)
 * 둘 다 identity token 을 백엔드 POST /auth/apple 로 보내서 우리 토큰을 받음
 */
import { api } from "./api";
import { isIOSApp, postNative } from "./native";

const WEB_CLIENT_ID = "app.ourtoday.web";
/** Apple Developer 에 등록한 반환 URL. 팝업 방식이라 실제로 이동하지는 않지만 일치해야 함 */
const WEB_REDIRECT_URI = "https://ourtoday.vercel.app/login/oauth2/code/apple";
const SDK_URL = "https://appleid.cdn-apple.com/appleauth/static/jsapi/appleid/1/ko_KR/appleid.auth.js";
const NATIVE_EVENT = "ourtoday:appleLogin";

type AppleCredential = { identityToken: string; authorizationCode?: string };

export class AppleLoginCancelled extends Error {}

declare global {
  interface Window {
    AppleID?: {
      auth: {
        init: (config: Record<string, unknown>) => void;
        signIn: () => Promise<{ authorization: { id_token: string; code?: string } }>;
      };
    };
  }
}

/** Android 앱은 팝업·네이티브 모두 없어서 숨김 (Apple 로그인 의무는 iOS 만) */
export function canUseAppleLogin(): boolean {
  if (typeof navigator === "undefined") return false;
  const isApp = /OurTodayApp/.test(navigator.userAgent);
  return !isApp || isIOSApp();
}

/** @returns 우리 서버 토큰. 사용자가 취소하면 AppleLoginCancelled */
export async function signInWithApple(): Promise<{ accessToken: string; refreshToken: string }> {
  const credential = isIOSApp() ? await nativeSignIn() : await webSignIn();
  return api("/auth/apple", { method: "POST", body: credential });
}

function nativeSignIn(): Promise<AppleCredential> {
  return new Promise((resolve, reject) => {
    const onResult = (e: Event) => {
      window.removeEventListener(NATIVE_EVENT, onResult);
      const d = (e as CustomEvent).detail as { identityToken?: string; authorizationCode?: string; error?: string };
      if (d?.identityToken) resolve({ identityToken: d.identityToken, authorizationCode: d.authorizationCode });
      else if (d?.error === "cancelled") reject(new AppleLoginCancelled());
      else reject(new Error("Apple 로그인에 실패했어요. 다시 시도해 주세요."));
    };
    window.addEventListener(NATIVE_EVENT, onResult);
    if (!postNative({ type: "appleLogin" })) {
      window.removeEventListener(NATIVE_EVENT, onResult);
      reject(new Error("앱을 최신 버전으로 업데이트해 주세요."));
    }
  });
}

let sdkPromise: Promise<void> | null = null;

/** 웹 브라우저에서만 Apple JS SDK 를 미리 받아 둠. 실패해도 누를 때 다시 시도하므로 무시 */
export function preloadAppleSdk(): void {
  if (isIOSApp()) return;
  loadSdk().catch(() => {});
}

function loadSdk(): Promise<void> {
  if (window.AppleID) return Promise.resolve();
  sdkPromise ??= new Promise<void>((resolve, reject) => {
    const script = document.createElement("script");
    script.src = SDK_URL;
    script.async = true;
    script.onload = () => resolve();
    script.onerror = () => {
      sdkPromise = null;
      reject(new Error("Apple 로그인을 불러오지 못했어요. 잠시 후 다시 시도해 주세요."));
    };
    document.head.appendChild(script);
  });
  return sdkPromise;
}

async function webSignIn(): Promise<AppleCredential> {
  await loadSdk();
  const AppleID = window.AppleID!;
  // 이름·이메일은 받지 않음 (회원 식별값만 사용)
  AppleID.auth.init({ clientId: WEB_CLIENT_ID, scope: "", redirectURI: WEB_REDIRECT_URI, usePopup: true });
  try {
    const res = await AppleID.auth.signIn();
    return { identityToken: res.authorization.id_token, authorizationCode: res.authorization.code };
  } catch (err) {
    const code = (err as { error?: string })?.error;
    if (code === "popup_closed_by_user" || code === "user_cancelled_authorize") throw new AppleLoginCancelled();
    throw new Error("Apple 로그인에 실패했어요. 다시 시도해 주세요.");
  }
}
