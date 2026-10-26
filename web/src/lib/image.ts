import { api } from "./api";
import type { PresignedUrl } from "./types";

const MAX_EDGE = 1920;
const MAX_BYTES = 10 * 1024 * 1024;

/** 큰 사진은 긴 변 1920px JPEG 로 줄여서 올림 (용량·속도). 디코딩 못 하는 형식(HEIC 등)은 원본 */
export async function compressImage(file: File): Promise<Blob> {
  try {
    const bitmap = await createImageBitmap(file, { imageOrientation: "from-image" });
    const scale = Math.min(1, MAX_EDGE / Math.max(bitmap.width, bitmap.height));
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
    return blob ?? file;
  } catch {
    return file;
  }
}

/** presigned URL 발급 → 저장소에 직접 PUT → imageKey 반환 */
export async function uploadImage(file: File): Promise<string> {
  const blob = await compressImage(file);
  const contentType = blob.type || file.type || "image/jpeg";
  if (blob.size > MAX_BYTES) throw new Error("사진은 10MB 이하만 올릴 수 있어요.");
  const presigned = await api<PresignedUrl>("/images/presigned-url", {
    method: "POST",
    body: { contentType, contentLength: blob.size },
  });
  const res = await fetch(presigned.uploadUrl, { method: "PUT", headers: { "Content-Type": contentType }, body: blob });
  if (!res.ok) throw new Error("사진 업로드에 실패했어요.");
  return presigned.imageKey;
}
