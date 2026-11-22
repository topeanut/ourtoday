/**
 * 휴대폰(앱·모바일 브라우저)에서 카카오톡 앱으로 바로 로그인
 *
 * 서버 주소(/oauth2/authorization/kakao)로 보내면 카카오가 웹 계정 로그인 화면(아이디·비밀번호)으로만 보내서
 * 카카오톡 앱이 열리지 않음. 카카오 JS SDK 의 Kakao.Auth.authorize 는 휴대폰에서 카카오톡을 바로 염
 * (Android: intent:, iOS: talk.kakao.com 유니버설 링크) → 동의하면 이 페이지가 결과를 확인(폴링)하다가
 * 서버 콜백(/login/oauth2/code/kakao)으로 이동. state 는 서버가 세션에 미리 저장해 둔 값을 받아서 씀
 *
 * 카카오톡이 없으면 SDK 가 웹 로그인 화면으로 넘어가고, 준비 중 오류가 나면 기존 방식으로 보냄
 */

const SDK_URL = "https://t1.kakaocdn.net/kakao_js_sdk/2.7.4/kakao.min.js";
const SDK_INTEGRITY = "sha384-DKYJZ8NLiK8MN4/C5P2dtSmLQ4KwPaoqAfyA/DfmEc1VDxu4yyC7wy6K1Hs90nka";
/** 카카오 JavaScript 키 (공개용. 카카오 콘솔에 등록한 도메인에서만 동작) */
const JS_KEY = "95aace48dcc318ab3bdb557f5c910ec3";
/** JS 키에 도메인·리다이렉트 URI 를 등록한 곳. 다른 곳(개발 서버·로컬)은 기존 방식 */
const SDK_ORIGIN = "https://ourtoday.vercel.app";
const WEB_LOGIN_URL = "/oauth2/authorization/kakao";

interface KakaoSdk {
  init(key: string): void;
  isInitialized(): boolean;
  Auth: { authorize(settings: { redirectUri: string; state?: string }): Promise<unknown> | void };
}

declare global {
  interface Window {
    Kakao?: KakaoSdk;
  }
}

function canUseTalkLogin(): boolean {
  return window.location.origin === SDK_ORIGIN && /Android|iPhone|iPod/i.test(navigator.userAgent);
}

let sdkPromise: Promise<KakaoSdk> | null = null;

function loadSdk(): Promise<KakaoSdk> {
  sdkPromise ??= new Promise<KakaoSdk>((resolve, reject) => {
    const done = () => {
      const Kakao = window.Kakao;
      if (!Kakao) return reject(new Error("kakao sdk missing"));
      if (!Kakao.isInitialized()) Kakao.init(JS_KEY);
      resolve(Kakao);
    };
    if (window.Kakao) return done();
    const script = document.createElement("script");
    script.src = SDK_URL;
    script.integrity = SDK_INTEGRITY;
    script.crossOrigin = "anonymous";
    script.async = true;
    script.onload = done;
    script.onerror = () => {
      sdkPromise = null;
      reject(new Error("kakao sdk load failed"));
    };
    document.head.appendChild(script);
  });
  return sdkPromise;
}

/** 로그인 화면에서 미리 받아 두면 누르자마자 카카오톡이 열림. 실패해도 누를 때 다시 시도 */
export function preloadKakaoSdk(): void {
  if (canUseTalkLogin()) loadSdk().catch(() => {});
}

export async function startKakaoLogin(): Promise<void> {
  if (!canUseTalkLogin()) {
    window.location.href = WEB_LOGIN_URL;
    return;
  }
  try {
    const [Kakao, prepared] = await Promise.all([
      loadSdk(),
      fetch("/api/v1/auth/kakao/prepare", { method: "POST", credentials: "same-origin" }).then((res) => {
        if (!res.ok) throw new Error(`prepare ${res.status}`);
        return res.json() as Promise<{ state: string; redirectUri: string }>;
      }),
    ]);
    // 결과는 SDK 가 redirectUri 로 이동시키며 넘겨줌 (거절·시간 초과도 같은 주소로 error 파라미터와 함께)
    Promise.resolve(Kakao.Auth.authorize({ redirectUri: prepared.redirectUri, state: prepared.state })).catch(() => {});
  } catch {
    window.location.href = WEB_LOGIN_URL;
  }
}
