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

    @Column(name = "profile_image_url")
    private String profileImageUrl;

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

    public void completeProfile(String nickname, String profileImageUrl) {
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.profileCompleted = true;
    }

    public void updateProfile(String nickname, String profileImageUrl) {
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
    }
}
