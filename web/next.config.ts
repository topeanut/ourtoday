import type { NextConfig } from "next";

// 백엔드. 같은 도메인으로 프록시해서 CORS·쿠키 문제 없이 사용
// 서버 주소는 저장소에 두지 않음 → Vercel 환경변수 BACKEND_ORIGIN (로컬 개발은 기본 localhost:8080)
const BACKEND = process.env.BACKEND_ORIGIN ?? (process.env.NODE_ENV === "production" ? undefined : "http://localhost:8080");
if (!BACKEND) throw new Error("BACKEND_ORIGIN 환경변수가 필요합니다 (예: http://<서버IP>:8080)");

const nextConfig: NextConfig = {
  poweredByHeader: false,
  async rewrites() {
    return [
      { source: "/api/:path*", destination: `${BACKEND}/api/:path*` },
      { source: "/oauth2/:path*", destination: `${BACKEND}/oauth2/:path*` },
      { source: "/login/oauth2/:path*", destination: `${BACKEND}/login/oauth2/:path*` },
      { source: "/swagger-ui.html", destination: `${BACKEND}/swagger-ui.html` },
      { source: "/swagger-ui/:path*", destination: `${BACKEND}/swagger-ui/:path*` },
      { source: "/v3/api-docs", destination: `${BACKEND}/v3/api-docs` },
      { source: "/v3/api-docs/:path*", destination: `${BACKEND}/v3/api-docs/:path*` },
    ];
  },
};

export default nextConfig;
