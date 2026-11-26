"use client";

import { useCallback, useEffect, useState } from "react";
import { api } from "./api";
import { getRefreshToken, getToken } from "./auth";
import { isApp, postNative } from "./native";

export interface LatLng {
  lat: number;
  lng: number;
}

export interface Sharing {
  mine: boolean;
  partner: boolean;
}

export interface PartnerLocation {
  partnerSharing: boolean;
  latest: LatLng | null;
  capturedAt: string | null;
  preciseActive: boolean;
}

export interface Track {
  path: LatLng[];
  pointCount: number;
}

export const getSharing = () => api<Sharing>("/location/sharing");

export async function setSharing(enabled: boolean) {
  const res = await api<Sharing>("/location/sharing", { method: "PUT", body: { enabled } });
  // 서버 반영 성공 후 네이티브에 공유 on/off 알림 (실패해도 토글은 성공 처리)
  if (isApp()) {
    try {
      // 켤 때 기존 로그인 세션 토큰도 네이티브에 전달 (신규 앱 설치 직후 대비)
      if (enabled) {
        const access = getToken();
        const refresh = getRefreshToken();
        if (access && refresh) postNative({ type: "authToken", access, refresh });
      }
      postNative({ type: "locationShare", enabled });
    } catch {
      // 브리지 오류 무시
    }
  }
  return res;
}

export const getPartner = () => api<PartnerLocation>("/location/partner");

export const requestPrecise = () => api<void>("/location/precise", { method: "POST" });

export const releasePrecise = () => api<void>("/location/precise", { method: "DELETE" });

export const getTrack = (date: string) =>
  api<Track>(`/location/track?date=${encodeURIComponent(date)}`);

const NORMAL_INTERVAL_MS = 25000;
const PRECISE_INTERVAL_MS = 7000;

/** active 일 때만 상대 위치를 폴링. precise 면 7초, 아니면 25초 간격 */
export function usePartnerLocation(
  active: boolean,
  precise: boolean,
): { data: PartnerLocation | null; error: unknown; refresh: () => void } {
  const [data, setData] = useState<PartnerLocation | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [tick, setTick] = useState(0);

  useEffect(() => {
    if (!active) return;
    let cancelled = false;

    const load = async () => {
      try {
        const res = await getPartner();
        if (cancelled) return;
        setData(res);
        setError(null);
      } catch (e) {
        if (!cancelled) setError(e);
      }
    };

    void load();
    const id = setInterval(() => void load(), precise ? PRECISE_INTERVAL_MS : NORMAL_INTERVAL_MS);

    return () => {
      cancelled = true;
      clearInterval(id);
    };
  }, [active, precise, tick]);

  const refresh = useCallback(() => setTick((t) => t + 1), []);

  return { data, error, refresh };
}
