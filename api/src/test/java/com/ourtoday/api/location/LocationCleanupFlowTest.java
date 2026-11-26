package com.ourtoday.api.location;

import com.ourtoday.api.auth.JwtTokenProvider;
import com.ourtoday.api.couple.CoupleService;
import com.ourtoday.api.push.FakePushSender;
import com.ourtoday.api.push.MutableClock;
import com.ourtoday.api.push.PushPlatform;
import com.ourtoday.api.push.PushToken;
import com.ourtoday.api.push.PushTokenRepository;
import com.ourtoday.api.user.UserService;
import com.ourtoday.api.user.dto.UserUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(MutableClock.Config.class)
class LocationCleanupFlowTest {

    @Autowired MockMvc mvc;
    @Autowired UserService userService;
    @Autowired CoupleService coupleService;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired PushTokenRepository pushTokenRepository;
    @Autowired LocationService locationService;
    @Autowired LocationPointRepository pointRepository;
    @Autowired PreciseRequestRepository requestRepository;
    @Autowired LocationDailyRepository dailyRepository;
    @Autowired LocationAccessLogRepository accessLogRepository;
    @Autowired FakePushSender pushSender;
    @Autowired MutableClock clock;

    UUID meId;
    UUID partnerId;
    String me;
    String partner;

    @BeforeEach
    void setUp() {
        pushSender.reset();
        clock.reset();
        meId = newUser("민지");
        partnerId = newUser("지호");
        coupleService.join(partnerId, coupleService.createInvite(meId).inviteCode());
        me = "Bearer " + tokenProvider.generateAccessToken(meId);
        partner = "Bearer " + tokenProvider.generateAccessToken(partnerId);
        pushTokenRepository.save(PushToken.create(partnerId, "partner-tok-" + partnerId, PushPlatform.ANDROID));
        pushTokenRepository.save(PushToken.create(meId, "me-tok-" + meId, PushPlatform.ANDROID));
    }

    @Test
    void 탈퇴하면_내_위치데이터가_지워진다() throws Exception {
        seedMyLocationData();

        mvc.perform(delete("/api/v1/users/me").header("Authorization", me)).andExpect(status().isNoContent());

        assertThat(pointRepository.findAll()).noneMatch(p -> p.getUserId().equals(meId));
        assertThat(requestRepository.findAll())
            .noneMatch(r -> r.getRequesterId().equals(meId) || r.getTargetId().equals(meId));
        assertThat(dailyRepository.findAll()).noneMatch(d -> d.getUserId().equals(meId));
        assertThat(locationService.isSharing(meId)).isFalse();
        assertThat(accessLogRepository.findAll()).hasSize(1);
    }

    @Test
    void 커플_해제하면_공유_꺼지고_상대에게_off() throws Exception {
        seedMyLocationData();
        toggle(partner, true);
        pushSender.reset();

        mvc.perform(delete("/api/v1/couples/me").header("Authorization", me)).andExpect(status().isNoContent());

        assertThat(locationService.isSharing(meId)).isFalse();
        assertThat(pointRepository.findAll()).noneMatch(p -> p.getUserId().equals(meId));
        assertThat(requestRepository.findAll())
            .noneMatch(r -> r.getRequesterId().equals(meId) || r.getTargetId().equals(meId));
        assertThat(dailyRepository.findAll()).anyMatch(d -> d.getUserId().equals(meId));
        List<FakePushSender.SentData> sent = pushSender.sentData();
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).token()).isEqualTo("partner-tok-" + partnerId);
        assertThat(sent.get(0).push().type()).isEqualTo("location_precise_off");
    }

    @Test
    void 커플_없는_사용자_탈퇴는_실패하지_않는다() throws Exception {
        UUID soloId = newUser("솔로");
        String solo = "Bearer " + tokenProvider.generateAccessToken(soloId);
        pointRepository.save(LocationPoint.of(soloId, 37.5, 127.0, 10.0, clock.instant(), PointSource.COARSE));

        mvc.perform(delete("/api/v1/users/me").header("Authorization", solo)).andExpect(status().isNoContent());

        assertThat(pointRepository.findAll()).noneMatch(p -> p.getUserId().equals(soloId));
        assertThat(pushSender.sentData()).isEmpty();
    }

    private void seedMyLocationData() throws Exception {
        toggle(me, true);
        Instant now = clock.instant();
        pointRepository.save(LocationPoint.of(meId, 37.5, 127.0, 10.0, now, PointSource.COARSE));
        pointRepository.save(LocationPoint.of(partnerId, 37.6, 127.1, 10.0, now, PointSource.COARSE));
        requestRepository.save(PreciseRequest.of(meId, partnerId, now.plus(Duration.ofMinutes(3))));
        requestRepository.save(PreciseRequest.of(partnerId, meId, now.plus(Duration.ofMinutes(3))));
        dailyRepository.save(LocationDaily.of(meId, LocalDate.of(2026, 11, 20), 2, now, now, "abc"));
        accessLogRepository.save(LocationAccessLog.of(partnerId, meId, AccessKind.PRECISE, now));
    }

    private void toggle(String token, boolean enabled) throws Exception {
        mvc.perform(put("/api/v1/location/sharing").header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":" + enabled + "}"))
            .andExpect(status().isOk());
    }

    private UUID newUser(String nickname) {
        UUID id = userService.findOrCreateByKakaoId("kakao-" + UUID.randomUUID()).getId();
        userService.updateMe(id, new UserUpdateRequest(nickname, null));
        return id;
    }
}
