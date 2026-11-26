/**
 * 카카오맵 JS SDK 로더 (services 라이브러리 포함 — 좌표 → 주소 변환용)
 * 키는 kakao.ts 와 같은 JavaScript 키(공개용, 카카오 콘솔에 등록한 도메인에서만 동작). 카카오맵 사용 설정 필요
 */

const JS_KEY = "95aace48dcc318ab3bdb557f5c910ec3";
const SDK_URL = `//dapi.kakao.com/v2/maps/sdk.js?appkey=${JS_KEY}&libraries=services&autoload=false`;

export interface PickedLocation {
  lat: number;
  lng: number;
  placeName: string;
}

export interface KakaoLatLng {
  getLat(): number;
  getLng(): number;
}
export interface KakaoMapInstance {
  setCenter(latlng: KakaoLatLng): void;
  relayout(): void;
  setBounds(bounds: KakaoLatLngBounds): void;
}
export interface KakaoLatLngBounds {
  extend(latlng: KakaoLatLng): void;
}
export interface KakaoPolylineInstance {
  setMap(map: KakaoMapInstance | null): void;
}
export interface KakaoCircleInstance {
  setMap(map: KakaoMapInstance | null): void;
}
export interface KakaoMarkerInstance {
  setPosition(latlng: KakaoLatLng): void;
  setMap(map: KakaoMapInstance | null): void;
  getPosition(): KakaoLatLng;
}
export interface KakaoMouseEvent {
  latLng: KakaoLatLng;
}
export interface KakaoAddressResult {
  address: { address_name: string } | null;
  road_address: { address_name: string; building_name?: string } | null;
}
export interface KakaoMaps {
  load(cb: () => void): void;
  LatLng: new (lat: number, lng: number) => KakaoLatLng;
  Map: new (container: HTMLElement, options: { center: KakaoLatLng; level: number }) => KakaoMapInstance;
  Marker: new (options: { position: KakaoLatLng; map?: KakaoMapInstance; draggable?: boolean }) => KakaoMarkerInstance;
  LatLngBounds: new () => KakaoLatLngBounds;
  Polyline: new (options: {
    path: KakaoLatLng[];
    strokeWeight?: number;
    strokeColor?: string;
    strokeOpacity?: number;
  }) => KakaoPolylineInstance;
  Circle: new (options: {
    center: KakaoLatLng;
    radius: number;
    strokeWeight?: number;
    strokeColor?: string;
    fillColor?: string;
    fillOpacity?: number;
  }) => KakaoCircleInstance;
  event: {
    addListener(target: object, type: string, handler: (e: KakaoMouseEvent) => void): void;
  };
  services: {
    Geocoder: new () => {
      coord2Address(
        lng: number,
        lat: number,
        cb: (result: KakaoAddressResult[], status: string) => void,
      ): void;
    };
    Status: { OK: string };
  };
}

declare global {
  interface Window {
    kakao?: { maps: KakaoMaps };
  }
}

let mapPromise: Promise<KakaoMaps> | null = null;

export function loadKakaoMap(): Promise<KakaoMaps> {
  mapPromise ??= new Promise<KakaoMaps>((resolve, reject) => {
    const ready = () => {
      const maps = window.kakao?.maps;
      if (!maps) return reject(new Error("kakao map missing"));
      maps.load(() => resolve(maps));
    };
    if (window.kakao?.maps) return ready();
    const script = document.createElement("script");
    script.src = SDK_URL;
    script.async = true;
    script.onload = ready;
    script.onerror = () => {
      mapPromise = null;
      script.remove();
      reject(new Error("kakao map sdk load failed"));
    };
    document.head.appendChild(script);
  });
  return mapPromise;
}
