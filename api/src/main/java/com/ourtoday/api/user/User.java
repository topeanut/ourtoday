package com.ourtoday.api.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    public static final int NICKNAME_MAX_LENGTH = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * 로그인 제공자 회원 식별값. 카카오는 회원번호 그대로, Apple 은 "apple:{sub}", 개발 로그인은 "dev:{이름}".
     * (컬럼 이름은 카카오만 있던 시절 그대로 — 운영 DB 마이그레이션 없이 쓰려고 유지)
     */
    @Column(name = "kakao_id", nullable = false, unique = true)
    private String kakaoId;

    /** Apple 로그인 사용자의 refresh token. 탈퇴 때 Apple 에 철회 요청용 */
    @Column(name = "apple_refresh_token", length = 1024)
    private String appleRefreshToken;

    /** refresh token 을 받은 Apple 클라이언트 (iOS Bundle ID 또는 웹 Services ID). 철회 때 같은 값 필요 */
    @Column(name = "apple_client_id")
    private String appleClientId;

    @Column(nullable = false)
    private String nickname;

    /** 직접 올린 프로필 사진 (저장소 키). 카카오 사진보다 우선 */
    @Column(name = "profile_image_key", length = 200)
    private String profileImageKey;

    /** 카카오 프로필 사진 URL. 카카오 로그인 때마다 갱신 */
    @Column(name = "kakao_profile_image_url", length = 500)
    private String kakaoProfileImageUrl;

    @Column(name = "profile_completed", nullable = false)
    private boolean profileCompleted;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static User createFromKakao(String kakaoId) {
        User user = new User();
        user.kakaoId = kakaoId;
        user.nickname = "";
        user.profileCompleted = false;
        return user;
    }

    public void updateAppleToken(String refreshToken, String clientId) {
        this.appleRefreshToken = refreshToken;
        this.appleClientId = clientId;
    }

    public void completeProfile(String nickname) {
        this.nickname = nickname;
        this.profileCompleted = true;
    }

    /** 가입 화면 기본값. 가입을 마친 뒤에는 바꾸지 않음 */
    public void prefillNickname(String nickname) {
        if (!profileCompleted) {
            this.nickname = nickname;
        }
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    /** @return 교체되어 더 이상 쓰지 않는 이전 키 (없으면 null) */
    public String changeProfileImageKey(String key) {
        String old = this.profileImageKey;
        this.profileImageKey = key;
        return old != null && !old.equals(key) ? old : null;
    }

    public void updateKakaoProfileImageUrl(String url) {
        this.kakaoProfileImageUrl = url;
    }
}
