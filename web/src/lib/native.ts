/**
 * 웹뷰 앱(Android/iOS) 브리지. 앱이 아니면 웹 API 로 대체
 *  - Android: window.OurTodayNative.postMessage(json)
 *  - iOS: window.webkit.messageHandlers.ourtoday.postMessage(obj)
 */
type NativeMessage =
  | { type: "share"; title?: string; text?: string; url: string }
  | { type: "openExternal"; url: string }
  | { type: "haptic" };

declare global {
  interface Window {
    OurTodayNative?: { postMessage: (json: string) => void };
    webkit?: { messageHandlers?: { ourtoday?: { postMessage: (msg: unknown) => void } } };
  }
}

export function isApp(): boolean {
  return typeof navigator !== "undefined" && /OurTodayApp/.test(navigator.userAgent);
}

function postNative(msg: NativeMessage): boolean {
  if (typeof window === "undefined") return false;
  if (window.OurTodayNative) {
    window.OurTodayNative.postMessage(JSON.stringify(msg));
    return true;
  }
  if (window.webkit?.messageHandlers?.ourtoday) {
    window.webkit.messageHandlers.ourtoday.postMessage(msg);
    return true;
  }
  return false;
}

/** @returns "shared" | "copied" | "cancelled" */
export async function share(data: { title?: string; text?: string; url: string }) {
  if (postNative({ type: "share", ...data })) return "shared";
  if (navigator.share) {
    try {
      await navigator.share(data);
      return "shared";
    } catch {
      return "cancelled";
    }
  }
  await navigator.clipboard.writeText(data.url);
  return "copied";
}

export function openExternal(url: string) {
  if (postNative({ type: "openExternal", url })) return;
  window.location.href = url;
}

export function haptic() {
  postNative({ type: "haptic" });
}
