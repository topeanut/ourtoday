"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { MapPin, CloseIcon } from "./Icons";
import { loadKakaoMap, type KakaoMapInstance, type KakaoMarkerInstance, type KakaoMaps } from "@/lib/kakaoMap";

export interface PickedLocation {
  lat: number;
  lng: number;
  placeName: string;
}

interface Props {
  value: PickedLocation | null;
  onChange: (v: PickedLocation | null) => void;
}

/** 서울시청 */
const DEFAULT_CENTER = { lat: 37.5665, lng: 126.978 };

export function LocationPicker({ value, onChange }: Props) {
  const boxRef = useRef<HTMLDivElement>(null);
  const mapsRef = useRef<KakaoMaps | null>(null);
  const mapRef = useRef<KakaoMapInstance | null>(null);
  const markerRef = useRef<KakaoMarkerInstance | null>(null);
  const onChangeRef = useRef(onChange);
  const valueRef = useRef(value);
  const [ready, setReady] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    onChangeRef.current = onChange;
    valueRef.current = value;
  });

  /** 좌표 -> 장소명 -> onChange. 변환 실패해도 좌표는 유지 */
  const pick = useCallback((lat: number, lng: number) => {
    const maps = mapsRef.current;
    const fallback = "선택한 위치";
    const emit = (placeName: string) => onChangeRef.current({ lat, lng, placeName });
    if (!maps) return emit(fallback);
    try {
      new maps.services.Geocoder().coord2Address(lng, lat, (result, status) => {
        if (status !== maps.services.Status.OK || !result[0]) return emit(fallback);
        const { road_address: road, address } = result[0];
        emit(road?.building_name || road?.address_name || address?.address_name || fallback);
      });
    } catch {
      emit(fallback);
    }
  }, []);

  // SDK 로드 + 지도 생성 (한 번)
  useEffect(() => {
    let cancelled = false;
    loadKakaoMap()
      .then((maps) => {
        if (cancelled || !boxRef.current) return;
        mapsRef.current = maps;
        const start = valueRef.current ?? DEFAULT_CENTER;
        const center = new maps.LatLng(start.lat, start.lng);
        const map = new maps.Map(boxRef.current, { center, level: 3 });
        const marker = new maps.Marker({ position: center, draggable: true });
        if (valueRef.current) marker.setMap(map);
        maps.event.addListener(map, "click", (e) => {
          marker.setPosition(e.latLng);
          marker.setMap(map);
          pick(e.latLng.getLat(), e.latLng.getLng());
        });
        maps.event.addListener(marker, "dragend", () => {
          const p = marker.getPosition();
          pick(p.getLat(), p.getLng());
        });
        mapRef.current = map;
        markerRef.current = marker;
        setReady(true);
      })
      .catch(() => {
        if (!cancelled) setNotice("지도를 불러오지 못했어요. 잠시 후 다시 시도해 주세요");
      });
    return () => {
      cancelled = true;
    };
  }, [pick]);

  // value 변경(현재 위치·지우기·외부 변경)을 지도에 반영
  useEffect(() => {
    const maps = mapsRef.current;
    const map = mapRef.current;
    const marker = markerRef.current;
    if (!ready || !maps || !map || !marker) return;
    if (!value) return marker.setMap(null);
    const pos = new maps.LatLng(value.lat, value.lng);
    marker.setPosition(pos);
    marker.setMap(map);
    map.setCenter(pos);
  }, [value, ready]);

  const useCurrent = () => {
    setNotice(null);
    if (!navigator.geolocation) {
      setNotice("현재 위치를 쓸 수 없어요. 지도를 눌러 직접 선택해 주세요");
      return;
    }
    setBusy(true);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setBusy(false);
        pick(pos.coords.latitude, pos.coords.longitude);
      },
      () => {
        setBusy(false);
        setNotice("현재 위치를 가져오지 못했어요. 지도를 눌러 직접 선택해 주세요");
      },
      { enableHighAccuracy: false, timeout: 10000, maximumAge: 60000 },
    );
  };

  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center gap-2">
        <button
          type="button"
          onClick={useCurrent}
          disabled={busy}
          className="flex items-center gap-1 rounded-full bg-brand-soft px-3 py-1.5 text-sm font-medium text-brand-strong disabled:opacity-50"
        >
          <MapPin className="h-4 w-4" />
          {busy ? "찾는 중..." : "현재 위치"}
        </button>
        {value && (
          <button
            type="button"
            onClick={() => {
              setNotice(null);
              onChange(null);
            }}
            className="flex items-center gap-1 rounded-full px-3 py-1.5 text-sm text-sub hover:bg-black/5"
          >
            <CloseIcon className="h-4 w-4" />
            지우기
          </button>
        )}
      </div>
      <div ref={boxRef} className="h-56 w-full overflow-hidden rounded-2xl bg-black/5" />
      {value && <p className="text-sm font-medium">{value.placeName}</p>}
      {!value && !notice && <p className="text-xs text-sub">지도를 눌러 위치를 선택하세요</p>}
      {notice && <p className="text-xs text-sub">{notice}</p>}
    </div>
  );
}
