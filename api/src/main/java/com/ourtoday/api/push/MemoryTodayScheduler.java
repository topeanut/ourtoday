package com.ourtoday.api.push;

import com.ourtoday.api.couple.Couple;
import com.ourtoday.api.couple.CoupleMember;
import com.ourtoday.api.couple.CoupleMemberRepository;
import com.ourtoday.api.couple.CoupleRepository;
import com.ourtoday.api.couple.CoupleStatus;
import com.ourtoday.api.post.Post;
import com.ourtoday.api.post.PostRepository;
import com.ourtoday.api.user.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * 매일 20:00(KST) 같은 월·일에 지난 해 기록이 있는 연결된 커플에게 알린다 (홈 "작년 오늘" 카드와 같은 기준).
 * 본문에 기록 내용은 넣지 않으며, 한 커플의 실패가 다른 커플 처리를 막지 않는다
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryTodayScheduler {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final CoupleRepository coupleRepository;
    private final CoupleMemberRepository coupleMemberRepository;
    private final PostRepository postRepository;
    private final UserSettingsRepository settingsRepository;
    private final PushDispatcher dispatcher;
    private final Clock clock;

    @Scheduled(cron = "0 0 20 * * *", zone = "Asia/Seoul")
    public void scheduled() {
        try {
            int sent = sendMemoryToday();
            log.info("작년 오늘 알림 발송 커플 수={}", sent);
        } catch (RuntimeException e) {
            log.warn("작년 오늘 알림 실행 실패", e);
        }
    }

    /** @return 알림을 보낸 커플 수 */
    public int sendMemoryToday() {
        LocalDate today = LocalDate.now(clock.withZone(SERVICE_ZONE));
        LocalDate yearStart = LocalDate.of(today.getYear(), 1, 1);
        int count = 0;
        for (Couple couple : coupleRepository.findByStatus(CoupleStatus.CONNECTED)) {
            try {
                if (notifyCouple(couple, today, yearStart)) {
                    count++;
                }
            } catch (RuntimeException e) {
                log.warn("작년 오늘 알림 실패 coupleId={}", couple.getId(), e);
            }
        }
        return count;
    }

    private boolean notifyCouple(Couple couple, LocalDate today, LocalDate yearStart) {
        List<Post> memories = postRepository.findMemories(
            couple.getId(), today.getMonthValue(), today.getDayOfMonth(), yearStart);
        if (memories.isEmpty()) {
            return false;
        }
        List<UUID> recipients = coupleMemberRepository.findByCoupleIdOrderByJoinedAtAsc(couple.getId()).stream()
            .map(CoupleMember::getUserId)
            .filter(id -> settingsRepository.findById(id).map(s -> s.isNotifyMemoryToday()).orElse(true))
            .toList();
        if (recipients.isEmpty()) {
            return false;
        }
        // findMemories 는 최신순이라 첫 항목이 가장 최근 연도
        int yearsAgo = today.getYear() - memories.get(0).getPostedDate().getYear();
        dispatcher.sendToUsers(recipients, new PushMessage(
            yearsAgo + "년 전 오늘, 둘이 남긴 기록이 있어요", "/", "memory_today"));
        return true;
    }
}
