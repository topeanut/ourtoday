package com.ourtoday.api.couple;

import com.ourtoday.api.common.error.BusinessException;
import com.ourtoday.api.common.error.ErrorCode;
import com.ourtoday.api.couple.dto.CoupleInviteResponse;
import com.ourtoday.api.couple.dto.CoupleResponse;
import com.ourtoday.api.user.User;
import com.ourtoday.api.user.UserService;
import com.ourtoday.api.user.dto.UserUpdateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class CoupleServiceTest {

    @Autowired CoupleService coupleService;
    @Autowired UserService userService;
    @Autowired CoupleRepository coupleRepository;

    @Test
    void 초대코드는_6자리_대문자숫자이고_24시간_뒤_만료된다() {
        UUID a = newUser("a");

        CoupleInviteResponse invite = coupleService.createInvite(a);

        assertThat(invite.inviteCode()).matches("[A-HJ-NP-Z2-9]{6}");
        assertThat(invite.expiresAt()).isBetween(
            Instant.now().plus(CoupleService.INVITE_TTL).minusSeconds(60),
            Instant.now().plus(CoupleService.INVITE_TTL).plusSeconds(60));
        assertThat(coupleRepository.findByMemberUserId(a)).get()
            .extracting(Couple::getStatus).isEqualTo(CoupleStatus.PENDING);
    }

    @Test
    void 대기중_재요청시_같은_커플에서_코드만_바뀐다() {
        UUID a = newUser("a");
        CoupleInviteResponse first = coupleService.createInvite(a);
        UUID coupleId = coupleRepository.findByMemberUserId(a).orElseThrow().getId();

        CoupleInviteResponse second = coupleService.createInvite(a);

        assertThat(second.inviteCode()).isNotEqualTo(first.inviteCode());
        assertThat(coupleRepository.findByMemberUserId(a).orElseThrow().getId()).isEqualTo(coupleId);
        assertThat(coupleRepository.count()).isEqualTo(1);
    }

    @Test
    void 합류하면_CONNECTED_되고_코드는_지워지며_사귄날짜는_오늘이다() {
        UUID a = newUser("a");
        UUID b = newUser("b");
        String code = coupleService.createInvite(a).inviteCode();

        CoupleResponse res = coupleService.join(b, code.toLowerCase());

        assertThat(res.status()).isEqualTo(CoupleStatus.CONNECTED);
        assertThat(res.startedAt()).isEqualTo(LocalDate.now(ZoneId.of("Asia/Seoul")));
        assertThat(res.members()).extracting(CoupleResponse.CoupleMemberResponse::id).containsExactly(a, b);
        assertThat(coupleRepository.existsByInviteCode(code)).isFalse();
    }

    @Test
    void 없는_코드는_INVITE_CODE_INVALID() {
        UUID b = newUser("b");

        assertError(() -> coupleService.join(b, "ZZZZZZ"), ErrorCode.INVITE_CODE_INVALID);
    }

    @Test
    void 만료된_코드는_INVITE_CODE_EXPIRED() {
        UUID a = newUser("a");
        UUID b = newUser("b");
        String code = coupleService.createInvite(a).inviteCode();
        coupleRepository.findByMemberUserId(a).orElseThrow()
            .reissueInviteCode(code, Instant.now().minusSeconds(1));

        assertError(() -> coupleService.join(b, code), ErrorCode.INVITE_CODE_EXPIRED);
    }

    @Test
    void 본인_코드로는_합류할_수_없다() {
        UUID a = newUser("a");
        String code = coupleService.createInvite(a).inviteCode();

        assertError(() -> coupleService.join(a, code), ErrorCode.INVITE_CODE_INVALID);
    }

    @Test
    void 내가_만든_대기_커플은_다른_코드로_합류하면_정리된다() {
        UUID a = newUser("a");
        UUID b = newUser("b");
        String codeA = coupleService.createInvite(a).inviteCode();
        coupleService.createInvite(b);

        coupleService.join(b, codeA);

        assertThat(coupleRepository.count()).isEqualTo(1);
        assertThat(coupleRepository.findByMemberUserId(b).orElseThrow().isConnected()).isTrue();
    }

    @Test
    void 이미_연결된_유저는_초대도_합류도_ALREADY_IN_COUPLE() {
        UUID a = newUser("a");
        UUID b = newUser("b");
        UUID c = newUser("c");
        coupleService.join(b, coupleService.createInvite(a).inviteCode());
        String codeC = coupleService.createInvite(c).inviteCode();

        assertError(() -> coupleService.createInvite(a), ErrorCode.ALREADY_IN_COUPLE);
        assertError(() -> coupleService.join(a, codeC), ErrorCode.ALREADY_IN_COUPLE);
    }

    @Test
    void 연결_전_내_커플_조회는_COUPLE_REQUIRED() {
        UUID a = newUser("a");
        coupleService.createInvite(a);

        assertError(() -> coupleService.getMyCouple(a), ErrorCode.COUPLE_REQUIRED);
    }

    private UUID newUser(String name) {
        User user = userService.findOrCreateByKakaoId("kakao-" + name + "-" + UUID.randomUUID());
        userService.updateMe(user.getId(), new UserUpdateRequest(name, null));
        return user.getId();
    }

    private void assertError(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(expected);
    }
}
