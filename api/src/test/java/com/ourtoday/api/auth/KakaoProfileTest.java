package com.ourtoday.api.auth;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KakaoProfileTest {

    @Test
    void 카카오_계정_프로필_사진을_https_로_꺼낸다() {
        Map<String, Object> attrs = Map.of("id", 1L, "kakao_account", Map.of("profile", Map.of(
            "profile_image_url", "http://k.kakaocdn.net/dn/a.jpg", "is_default_image", false)));
        assertThat(KakaoProfile.imageUrl(attrs)).isEqualTo("https://k.kakaocdn.net/dn/a.jpg");
    }

    @Test
    void 카카오_기본_이미지는_사진_없음으로_본다() {
        Map<String, Object> attrs = Map.of("id", 1L, "kakao_account", Map.of("profile", Map.of(
            "profile_image_url", "https://k.kakaocdn.net/dn/default.jpg", "is_default_image", true)));
        assertThat(KakaoProfile.imageUrl(attrs)).isNull();
    }

    @Test
    void 예전_형식_properties_도_읽는다() {
        Map<String, Object> attrs = Map.of("id", 1L, "properties", Map.of("profile_image", "https://k.kakaocdn.net/dn/b.jpg"));
        assertThat(KakaoProfile.imageUrl(attrs)).isEqualTo("https://k.kakaocdn.net/dn/b.jpg");
    }

    @Test
    void 동의하지_않아_값이_없으면_null() {
        assertThat(KakaoProfile.imageUrl(Map.of("id", 1L))).isNull();
        assertThat(KakaoProfile.imageUrl(Map.of("id", 1L, "kakao_account", Map.of()))).isNull();
    }

    @Test
    void 닉네임은_공백을_빼고_최대_길이로_자른다() {
        Map<String, Object> attrs = Map.of("id", 1L, "kakao_account", Map.of("profile", Map.of("nickname", "  아주아주아주아주아주아주아주아주긴닉네임입니다  ")));
        assertThat(KakaoProfile.nickname(attrs, 20)).isEqualTo("아주아주아주아주아주아주아주아주긴닉네임");
        assertThat(KakaoProfile.nickname(Map.of("id", 1L, "properties", Map.of("nickname", "지호")), 20)).isEqualTo("지호");
        assertThat(KakaoProfile.nickname(Map.of("id", 1L), 20)).isNull();
    }
}
