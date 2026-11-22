import { api } from "./api";
import type { PresignedUrl } from "./types";

const MAX_EDGE = 1920;
const MAX_BYTES = 10 * 1024 * 1024;

const BROWSER_SAFE = ["image/jpeg", "image/png", "image/webp"];

/**
 * 큰 사진은 긴 변 1920px JPEG 로 줄여서 올림 (용량·속도).
 * HEIC 처럼 이 브라우저가 못 읽는 형식은 다른 기기에서도 깨져 보이므로 거부
 */
export async function compressImage(file: File, maxEdge = MAX_EDGE): Promise<Blob> {
  let bitmap: ImageBitmap;
  try {
    bitmap = await createImageBitmap(file, { imageOrientation: "from-image" });
  } catch {
    if (BROWSER_SAFE.includes(file.type)) return file;
    throw new Error("이 사진 형식은 올릴 수 없어요. JPG나 PNG로 바꿔서 올려 주세요.");
  }
  try {
    const scale = Math.min(1, maxEdge / Math.max(bitmap.width, bitmap.height));
    if (scale === 1 && file.size < 1.5 * 1024 * 1024 && file.type === "image/jpeg") {
      bitmap.close();
      return file;
    }
    const canvas = document.createElement("canvas");
    canvas.width = Math.round(bitmap.width * scale);
    canvas.height = Math.round(bitmap.height * scale);
    canvas.getContext("2d")!.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    bitmap.close();
    const blob = await new Promise<Blob | null>((r) => canvas.toBlob(r, "image/jpeg", 0.85));
    if (blob) return blob;
  } catch {
    // 아래에서 처리
  }
  if (BROWSER_SAFE.includes(file.type)) return file;
  throw new Error("이 사진 형식은 올릴 수 없어요. JPG나 PNG로 바꿔서 올려 주세요.");
}

/**
 * presigned URL 발급 → 저장소에 직접 PUT → imageKey 반환.
 * 기본은 게시글 사진(커플 연결 필요). 프로필 사진은 uploadProfileImage 사용
 */
export async function uploadImage(file: File, { path = "/images/presigned-url", maxEdge = MAX_EDGE } = {}): Promise<string> {
  const blob = await compressImage(file, maxEdge);
  const contentType = blob.type || file.type;
  if (!BROWSER_SAFE.includes(contentType)) throw new Error("이 사진 형식은 올릴 수 없어요. JPG나 PNG로 바꿔서 올려 주세요.");
  if (blob.size > MAX_BYTES) throw new Error("사진은 10MB 이하만 올릴 수 있어요.");
  const presigned = await api<PresignedUrl>(path, {
    method: "POST",
    body: { contentType, contentLength: blob.size },
  });
  const res = await fetch(presigned.uploadUrl, { method: "PUT", headers: { "Content-Type": contentType }, body: blob });
  if (!res.ok) throw new Error("사진 업로드에 실패했어요.");
  return presigned.imageKey;
}

/** 프로필 사진: 가입 중(커플 연결 전)에도 올릴 수 있는 경로, 작게 줄여서 */
export function uploadProfileImage(file: File): Promise<string> {
  return uploadImage(file, { path: "/users/me/profile-image/presigned-url", maxEdge: 640 });
}
