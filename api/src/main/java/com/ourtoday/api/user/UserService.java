package com.ourtoday.api.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;

    @Transactional
    public User findOrCreateByKakaoId(String kakaoId) {
        return userRepository.findByKakaoId(kakaoId)
            .orElseGet(() -> {
                User user = userRepository.save(User.createFromKakao(kakaoId));
                userSettingsRepository.save(UserSettings.createDefault(user));
                return user;
            });
    }
}
