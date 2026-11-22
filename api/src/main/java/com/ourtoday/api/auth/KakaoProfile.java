package com.ourtoday.api.auth;

import java.util.Map;

/**
 * 카카오 사용자 정보(/v2/user/me)에서 프로필 사진 URL·닉네임 추출.
 * 카카오 개발자 콘솔에서 "프로필 사진"·"닉네임" 동의 항목(선택 동의)을 켜 두었고, 사용자가 동의해야 값이 들어옴
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

    /** @return 앞뒤 공백을 뺀 카카오 닉네임 (최대 maxLength 자), 동의 안 했으면 null */
    static String nickname(Map<String, Object> attributes, int maxLength) {
        Object name = map(map(attributes.get("kakao_account")).get("profile")).get("nickname");
        if (name == null) {
            name = map(attributes.get("properties")).get("nickname");
        }
        if (!(name instanceof String s) || s.isBlank()) {
            return null;
        }
        String trimmed = s.strip();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    private static Map<?, ?> map(Object value) {
        return value instanceof Map<?, ?> m ? m : Map.of();
    }
}
