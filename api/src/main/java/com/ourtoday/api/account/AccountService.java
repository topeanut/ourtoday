package com.ourtoday.api.account;

import com.ourtoday.api.calendar.CalendarMemoRepository;
import com.ourtoday.api.couple.CoupleService;
import com.ourtoday.api.image.ImageService;
import com.ourtoday.api.post.PostService;
import com.ourtoday.api.reaction.ReactionRepository;
import com.ourtoday.api.user.UserRepository;
import com.ourtoday.api.user.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 회원 탈퇴 (앱스토어 정책상 앱 안에서 탈퇴 가능해야 함)
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    private final CoupleService coupleService;
    private final PostService postService;
    private final ReactionRepository reactionRepository;
    private final CalendarMemoRepository calendarMemoRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final UserRepository userRepository;
    private final ImageService imageService;

    /**
     * 커플은 해제(상대는 커플 없음 상태), 내가 쓴 글·사진·반응·메모와 계정 삭제.
     * 상대가 쓴 기록은 상대 것이라 남김 (해제된 커플이라 조회는 불가)
     */
    @Transactional
    public void withdraw(UUID userId) {
        coupleService.leaveAny(userId);
        postService.deleteAllWrittenBy(userId);
        reactionRepository.deleteByUserId(userId);
        calendarMemoRepository.deleteByAuthorId(userId);
        userSettingsRepository.findById(userId).ifPresent(userSettingsRepository::delete);
        userRepository.deleteById(userId);
        // 내보내기 zip 에는 상대 기록도 들어 있어서 탈퇴 시 함께 삭제
        imageService.deletePrefixAfterCommit("exports/" + userId + "/");
    }
}
