import type { NextConfig } from "next";

// 백엔드(Oracle VM). 같은 도메인으로 프록시해서 CORS·쿠키 문제 없이 사용
const BACKEND = process.env.BACKEND_ORIGIN ?? "http://129.225.158.235:8080";

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
