/** 개발 서버(develop-ourtoday.vercel.app) 빌드에서만 true — NEXT_PUBLIC_DEV=1 */
export const IS_DEV = process.env.NEXT_PUBLIC_DEV === "1";
