package com.ourtoday.api.account;

import com.ourtoday.api.auth.apple.AppleAuthClient;
import com.ourtoday.api.calendar.CalendarMemoRepository;
import com.ourtoday.api.comment.CommentRepository;
import com.ourtoday.api.couple.CoupleService;
import com.ourtoday.api.image.ImageService;
import com.ourtoday.api.post.PostService;
import com.ourtoday.api.reaction.ReactionRepository;
import com.ourtoday.api.user.User;
import com.ourtoday.api.user.UserRepository;
import com.ourtoday.api.user.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * 회원 탈퇴 (앱스토어 정책상 앱 안에서 탈퇴 가능해야 함)
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    private final CoupleService coupleService;
    private final PostService postService;
    private final CommentRepository commentRepository;
    private final ReactionRepository reactionRepository;
    private final CalendarMemoRepository calendarMemoRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final UserRepository userRepository;
    private final ImageService imageService;
    private final AppleAuthClient appleAuthClient;

    /**
     * 커플은 해제(상대는 커플 없음 상태), 내가 쓴 글·사진·반응·메모와 계정 삭제.
     * 상대가 쓴 기록은 상대 것이라 남김 (해제된 커플이라 조회는 불가)
     */
    @Transactional
    public void withdraw(UUID userId) {
        userRepository.findById(userId).ifPresent(this::revokeAppleAfterCommit);
        coupleService.leaveAny(userId);
        postService.deleteAllWrittenBy(userId);
        commentRepository.deleteByAuthorId(userId);
        reactionRepository.deleteByUserId(userId);
        calendarMemoRepository.deleteByAuthorId(userId);
        userSettingsRepository.findById(userId).ifPresent(userSettingsRepository::delete);
        userRepository.deleteById(userId);
        // 내보내기 zip 에는 상대 기록도 들어 있어서 탈퇴 시 함께 삭제
        imageService.deletePrefixAfterCommit("exports/" + userId + "/");
    }

    /** Apple 로그인 사용자는 탈퇴가 확정된 뒤 Apple 연결도 끊음 (심사 지침 5.1.1(v)) */
    private void revokeAppleAfterCommit(User user) {
        String refreshToken = user.getAppleRefreshToken();
        String clientId = user.getAppleClientId();
        if (refreshToken == null) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                appleAuthClient.revoke(refreshToken, clientId);
            }
        });
    }
}
