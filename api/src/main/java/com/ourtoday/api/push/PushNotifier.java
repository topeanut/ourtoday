package com.ourtoday.api.push;

import com.ourtoday.api.couple.Couple;
import com.ourtoday.api.couple.CoupleMember;
import com.ourtoday.api.couple.CoupleMemberRepository;
import com.ourtoday.api.couple.CoupleRepository;
import com.ourtoday.api.post.Post;
import com.ourtoday.api.post.PostRepository;
import com.ourtoday.api.push.event.CoupleConnectedEvent;
import com.ourtoday.api.push.event.PostCreatedEvent;
import com.ourtoday.api.push.event.ReactionAddedEvent;
import com.ourtoday.api.user.UserRepository;
import com.ourtoday.api.user.UserSettings;
import com.ourtoday.api.user.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * 도메인 이벤트(커밋 후)를 푸시 알림으로 바꾼다. 알림 제목은 고정이고 본문에 기록 내용은 넣지 않는다.
 * 어떤 실패도 원 요청(기록·반응·연결)에 영향을 주지 않도록 리스너 안에서 모두 삼킨다
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PushNotifier {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
    private static final Duration REACTION_DEDUPE = Duration.ofMinutes(10);
    private static final int DEDUPE_CLEANUP_THRESHOLD = 1000;

    private final PushDispatcher dispatcher;
    private final CoupleRepository coupleRepository;
    private final CoupleMemberRepository coupleMemberRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final UserSettingsRepository settingsRepository;
    private final Clock clock;

    private final ConcurrentHashMap<String, Instant> recentReactions = new ConcurrentHashMap<>();

    @Async(PushConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostCreated(PostCreatedEvent event) {
        safely("partner_post", () -> {
            if (!isConnected(event.coupleId())) {
                return;
            }
            Optional<UUID> partner = otherMemberIds(event.coupleId(), event.authorId()).stream().findFirst();
            if (partner.isEmpty() || !allows(partner.get(), UserSettings::isNotifyPartnerPost)) {
                return;
            }
            String name = nicknameOf(event.authorId());
            LocalDate today = LocalDate.now(clock.withZone(SERVICE_ZONE));
            LocalDate posted = event.postedDate();
            String what = posted.equals(today)
                ? "오늘의 기록"
                : posted.getMonthValue() + "월 " + posted.getDayOfMonth() + "일의 기록";
            dispatcher.sendToUsers(List.of(partner.get()), new PushMessage(
                name + Josa.iGa(name) + " " + what + "을 남겼어요",
                "/posts/" + event.postId(), "partner_post"));
        });
    }

    @Async(PushConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReactionAdded(ReactionAddedEvent event) {
        safely("reaction", () -> {
            Post post = postRepository.findById(event.postId()).orElse(null);
            if (post == null || post.getAuthorId().equals(event.reactorId())
                || !isConnected(post.getCoupleId())
                || !allows(post.getAuthorId(), UserSettings::isNotifyReaction)
                || isRecentDuplicate(event.reactorId(), event.postId())) {
                return;
            }
            String name = nicknameOf(event.reactorId());
            dispatcher.sendToUsers(List.of(post.getAuthorId()), new PushMessage(
                name + Josa.iGa(name) + " 내 기록에 반응했어요", "/posts/" + event.postId(), "reaction"));
        });
    }

    @Async(PushConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCoupleConnected(CoupleConnectedEvent event) {
        safely("couple_connected", () -> {
            if (!isConnected(event.coupleId())) {
                return;
            }
            Optional<UUID> inviter = otherMemberIds(event.coupleId(), event.joinerId()).stream().findFirst();
            if (inviter.isEmpty()) {
                return;
            }
            dispatcher.sendToUsers(List.of(inviter.get()), new PushMessage(
                nicknameOf(event.joinerId()) + "님과 연결됐어요. 첫 기록을 남겨 보세요", "/", "couple_connected"));
        });
    }

    private boolean isConnected(UUID coupleId) {
        return coupleRepository.findById(coupleId).filter(Couple::isConnected).isPresent();
    }

    private List<UUID> otherMemberIds(UUID coupleId, UUID userId) {
        return coupleMemberRepository.findByCoupleIdOrderByJoinedAtAsc(coupleId).stream()
            .map(CoupleMember::getUserId)
            .filter(id -> !id.equals(userId))
            .toList();
    }

    /** 설정 행이 없으면 기본값(켜짐) */
    private boolean allows(UUID userId, Predicate<UserSettings> flag) {
        return settingsRepository.findById(userId).map(flag::test).orElse(true);
    }

    private String nicknameOf(UUID userId) {
        return userRepository.findById(userId).orElseThrow().getNickname();
    }

    /** 같은 (반응자, 기록) 은 10분 안에 한 번만 알린다. true 면 이미 알렸음 */
    private boolean isRecentDuplicate(UUID reactorId, UUID postId) {
        Instant now = clock.instant();
        if (recentReactions.size() > DEDUPE_CLEANUP_THRESHOLD) {
            recentReactions.values().removeIf(at -> at.plus(REACTION_DEDUPE).isBefore(now));
        }
        boolean[] duplicate = {false};
        recentReactions.compute(reactorId + ":" + postId, (key, last) -> {
            if (last != null && now.isBefore(last.plus(REACTION_DEDUPE))) {
                duplicate[0] = true;
                return last;
            }
            return now;
        });
        return duplicate[0];
    }

    private void safely(String type, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.warn("푸시 알림 준비 실패 type={}", type, e);
        }
    }
}
