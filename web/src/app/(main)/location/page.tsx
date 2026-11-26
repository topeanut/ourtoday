"use client";

import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { PageHeader } from "@/components/AppShell";
import { BackIcon, MapPin } from "@/components/Icons";
import { Button, Card, IconButton, Toggle } from "@/components/ui";
import { relativeTime, todayKst } from "@/lib/date";
import {
  getTrack,
  releasePrecise,
  requestPrecise,
  usePartnerLocation,
  type LatLng,
} from "@/lib/location";
import { goBack } from "@/lib/nav";
import {
  loadKakaoMap,
  type KakaoCircleInstance,
  type KakaoMapInstance,
  type KakaoMarkerInstance,
  type KakaoMaps,
  type KakaoPolylineInstance,
} from "@/lib/kakaoMap";

const PRECISE_DURATION_MS = 3 * 60 * 1000;
const DEFAULT_CENTER: LatLng = { lat: 37.5665, lng: 126.978 };

function formatRemaining(ms: number): string {
  const total = Math.max(0, Math.ceil(ms / 1000));
  const m = Math.floor(total / 60);
  const s = String(total % 60).padStart(2, "0");
  return `${m}:${s}`;
}

export default function LocationPage() {
  const router = useRouter();
  const boxRef = useRef<HTMLDivElement>(null);
  const mapsRef = useRef<KakaoMaps | null>(null);
  const mapRef = useRef<KakaoMapInstance | null>(null);
  const partnerMarkerRef = useRef<KakaoMarkerInstance | null>(null);
  const myCircleRef = useRef<KakaoCircleInstance | null>(null);
  const lineRef = useRef<KakaoPolylineInstance | null>(null);
  const centeredRef = useRef(false);

  const [ready, setReady] = useState(false);
  const [mapError, setMapError] = useState(false);
  const [preciseUntil, setPreciseUntil] = useState<number | null>(null);
  const [now, setNow] = useState(() => Date.now());
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [showRoute, setShowRoute] = useState(true);
  const [track, setTrack] = useState<LatLng[]>([]);
  const [me, setMe] = useState<LatLng | null>(null);

  const preciseOn = preciseUntil !== null;
  const { data } = usePartnerLocation(true, preciseOn);
  const sharingOff = data !== null && !data.partnerSharing;
  const latest = data?.partnerSharing ? data.latest : null;
  const capturedAt = data?.capturedAt ?? null;

  // 지도 생성 (한 번)
  useEffect(() => {
    let cancelled = false;
    loadKakaoMap()
      .then((maps) => {
        if (cancelled || !boxRef.current) return;
        mapsRef.current = maps;
        mapRef.current = new maps.Map(boxRef.current, {
          center: new maps.LatLng(DEFAULT_CENTER.lat, DEFAULT_CENTER.lng),
          level: 4,
        });
        setReady(true);
      })
      .catch(() => {
        if (!cancelled) setMapError(true);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  // 내 위치 1회 (표시 전용, 실패·거부 시 조용히 생략)
  useEffect(() => {
    if (!navigator.geolocation) return;
    navigator.geolocation.getCurrentPosition(
      (pos) => setMe({ lat: pos.coords.latitude, lng: pos.coords.longitude }),
      () => undefined,
      { enableHighAccuracy: false, timeout: 8000, maximumAge: 60000 },
    );
  }, []);

  // 지도가 다시 보일 때(공유 재개) 크기 재계산
  useEffect(() => {
    if (ready && !sharingOff) mapRef.current?.relayout();
  }, [ready, sharingOff]);

  // 상대 마커
  useEffect(() => {
    const maps = mapsRef.current;
    const map = mapRef.current;
    if (!ready || !maps || !map) return;
    if (!latest) {
      partnerMarkerRef.current?.setMap(null);
      return;
    }
    const pos = new maps.LatLng(latest.lat, latest.lng);
    if (!partnerMarkerRef.current) partnerMarkerRef.current = new maps.Marker({ position: pos });
    partnerMarkerRef.current.setPosition(pos);
    partnerMarkerRef.current.setMap(map);
    if (!centeredRef.current) {
      map.setCenter(pos);
      centeredRef.current = true;
    }
  }, [ready, latest]);

  // 내 위치 표시 (작은 원)
  useEffect(() => {
    const maps = mapsRef.current;
    const map = mapRef.current;
    if (!ready || !maps || !map || !me) return;
    myCircleRef.current?.setMap(null);
    const circle = new maps.Circle({
      center: new maps.LatLng(me.lat, me.lng),
      radius: 15,
      strokeWeight: 2,
      strokeColor: "#3b82f6",
      fillColor: "#3b82f6",
      fillOpacity: 0.7,
    });
    circle.setMap(sharingOff ? null : map);
    myCircleRef.current = circle;
  }, [ready, me, sharingOff]);

  // 오늘 경로 불러오기 (새 위치가 올 때마다 갱신)
  useEffect(() => {
    if (sharingOff) return;
    let cancelled = false;
    getTrack(todayKst())
      .then((res) => {
        if (!cancelled) setTrack(res.path);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [capturedAt, sharingOff]);

  // 경로 그리기
  useEffect(() => {
    const maps = mapsRef.current;
    const map = mapRef.current;
    if (!ready || !maps || !map) return;
    lineRef.current?.setMap(null);
    lineRef.current = null;
    if (!showRoute || sharingOff || track.length < 2) return;
    const line = new maps.Polyline({
      path: track.map((p) => new maps.LatLng(p.lat, p.lng)),
      strokeWeight: 5,
      strokeColor: "#ff6b81",
      strokeOpacity: 0.8,
    });
    line.setMap(map);
    lineRef.current = line;
  }, [ready, track, showRoute, sharingOff]);

  // 정밀 모드 카운트다운 (언마운트 시 정리)
  const releaseNow = useCallback(() => {
    setPreciseUntil(null);
    releasePrecise().catch(() => undefined);
  }, []);

  useEffect(() => {
    if (preciseUntil === null) return;
    const id = setInterval(() => {
      const t = Date.now();
      setNow(t);
      if (t >= preciseUntil) {
        clearInterval(id);
        releaseNow();
      }
    }, 1000);
    return () => clearInterval(id);
  }, [preciseUntil, releaseNow]);

  const togglePrecise = async () => {
    setNotice(null);
    if (preciseOn) {
      releaseNow();
      return;
    }
    setBusy(true);
    try {
      await requestPrecise();
      const t = Date.now();
      setNow(t);
      setPreciseUntil(t + PRECISE_DURATION_MS);
    } catch {
      setNotice("정확한 위치를 요청하지 못했어요. 잠시 후 다시 시도해 주세요");
    } finally {
      setBusy(false);
    }
  };

  const remaining = preciseUntil === null ? 0 : preciseUntil - now;

  return (
    <>
      <PageHeader
        title="상대 위치"
        left={
          <IconButton onClick={() => goBack(router)} label="뒤로" className="-ml-2.5">
            <BackIcon />
          </IconButton>
        }
      />
      <div className="mx-auto max-w-2xl px-4 pb-10 md:px-8">
        {sharingOff && (
          <Card className="mb-4 flex items-center gap-3">
            <MapPin className="h-5 w-5 text-sub" />
            <p className="font-semibold">상대가 위치 공유를 껐어요</p>
          </Card>
        )}

        <div className={sharingOff ? "hidden" : ""}>
          <p className="mb-3 flex items-center gap-2 text-sm font-medium text-sub">
            <MapPin className="h-4 w-4" />
            {latest && capturedAt
              ? `${preciseOn ? "정확한" : "대략"} 위치 · ${relativeTime(capturedAt)}`
              : data
                ? "위치 없음"
                : "불러오는 중"}
          </p>

          <div ref={boxRef} className="h-[55vh] min-h-72 w-full overflow-hidden rounded-3xl bg-black/5" />
          {mapError && <p className="mt-2 text-sm text-sub">지도를 불러오지 못했어요. 잠시 후 다시 시도해 주세요</p>}

          <Card className="mt-4 flex flex-col gap-4">
            <div className="flex items-center justify-between gap-3">
              <div>
                <p className="font-semibold">정확한 위치 보기</p>
                <p className="text-sm text-sub">
                  {preciseOn ? `남은 시간 ${formatRemaining(remaining)}` : "3분 동안 더 자주 갱신해요"}
                </p>
              </div>
              <Button
                variant={preciseOn ? "soft" : "primary"}
                size="sm"
                loading={busy}
                onClick={() => void togglePrecise()}
              >
                {preciseOn ? "끄기" : "켜기"}
              </Button>
            </div>
            {notice && <p className="text-sm text-[#d6404e]">{notice}</p>}

            <div className="flex items-center justify-between gap-3">
              <div>
                <p className="font-semibold">오늘 이동 경로</p>
                {track.length === 0 && <p className="text-sm text-sub">아직 경로가 없어요</p>}
                {track.length > 0 && <p className="text-sm text-sub">기록된 지점 {track.length}개</p>}
              </div>
              <Toggle checked={showRoute} onChange={setShowRoute} label="오늘 이동 경로 표시" />
            </div>
          </Card>
        </div>
      </div>
    </>
  );
}
