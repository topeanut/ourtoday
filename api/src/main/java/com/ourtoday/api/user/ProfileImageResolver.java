package com.ourtoday.api.user;

import com.ourtoday.api.image.ImageStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 프로필 사진 우선순위: 직접 올린 사진 → 카카오 프로필 사진 → 없음(null, 화면은 이름 첫 글자)
 * <p>
 * ImageService 대신 ImageStorage 를 직접 쓴다 — ImageService 는 CoupleService 에 의존해서,
 * CoupleService 가 이 클래스를 쓰면 순환 의존이 생김
 */
@Component
@RequiredArgsConstructor
public class ProfileImageResolver {

    private final ImageStorage storage;

    public String urlOf(User user) {
        if (user.getProfileImageKey() != null) {
            return storage.presignDownload(user.getProfileImageKey());
        }
        return user.getKakaoProfileImageUrl();
    }
}
