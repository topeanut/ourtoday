package com.ourtoday.api.auth;

import java.util.Map;

/**
 * 카카오 사용자 정보(/v2/user/me)에서 프로필 사진 URL 추출.
 * 카카오 개발자 콘솔에서 "프로필 사진" 동의 항목을 켜고, 사용자가 동의해야 값이 들어옴
 */
final class KakaoProfile {

    private KakaoProfile() {
    }

    /** @return https 프로필 사진 URL, 동의 안 했거나 카카오 기본 이미지면 null */
    static String imageUrl(Map<String, Object> attributes) {
        Map<?, ?> profile = map(map(attributes.get("kakao_account")).get("profile"));
        if (Boolean.TRUE.equals(profile.get("is_default_image"))) {
            return null;
        }
        Object url = profile.get("profile_image_url");
        if (url == null) {
            url = map(attributes.get("properties")).get("profile_image");
        }
        if (!(url instanceof String s) || s.isBlank()) {
            return null;
        }
        // 카카오가 http 로 줄 때가 있음 → https 페이지에서 혼합 콘텐츠로 막히지 않게
        return s.startsWith("http://") ? "https://" + s.substring("http://".length()) : s;
    }

    private static Map<?, ?> map(Object value) {
        return value instanceof Map<?, ?> m ? m : Map.of();
    }
}
