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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(MutableClock.Config.class)
class LocationPreciseFlowTest {

    @Autowired MockMvc mvc;
    @Autowired UserService userService;
    @Autowired CoupleService coupleService;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired PushTokenRepository pushTokenRepository;
    @Autowired PreciseRequestRepository requestRepository;
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
    void 정밀_요청하면_상대에게_무음푸시_간다() throws Exception {
        toggle(partner, true);

        mvc.perform(post("/api/v1/location/precise").header("Authorization", me))
            .andExpect(status().isNoContent());

        List<FakePushSender.SentData> sent = pushSender.sentData();
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).token()).isEqualTo("partner-tok-" + partnerId);
        assertThat(sent.get(0).push().type()).isEqualTo("location_precise_on");
        assertThat(sent.get(0).push().data().get("expiresAt"))
            .isEqualTo(clock.instant().plus(Duration.ofMinutes(3)).toString());
        List<LocationAccessLog> logs = accessLogRepository.findAll();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getViewerId()).isEqualTo(meId);
        assertThat(logs.get(0).getTargetId()).isEqualTo(partnerId);
        assertThat(logs.get(0).getKind()).isEqualTo(AccessKind.PRECISE);
        assertThat(logs.get(0).getStartedAt()).isEqualTo(clock.instant());

        mvc.perform(get("/api/v1/location/partner").header("Authorization", me))
            .andExpect(jsonPath("$.preciseActive").value(true));
    }

    @Test
    void 활성_중_재요청은_만료를_연장한다() throws Exception {
        mvc.perform(post("/api/v1/location/precise").header("Authorization", me)).andExpect(status().isNoContent());
        clock.advance(Duration.ofMinutes(1));
        mvc.perform(post("/api/v1/location/precise").header("Authorization", me)).andExpect(status().isNoContent());

        List<PreciseRequest> all = requestRepository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getExpiresAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(3)));
        assertThat(pushSender.sentData()).hasSize(2);
        assertThat(pushSender.sentData()).allMatch(s -> s.push().type().equals("location_precise_on"));
    }

    @Test
    void 해제하면_즉시_만료되고_off_푸시() throws Exception {
        toggle(partner, true);
        mvc.perform(post("/api/v1/location/precise").header("Authorization", me)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/location/precise").header("Authorization", me)).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/location/partner").header("Authorization", me))
            .andExpect(jsonPath("$.preciseActive").value(false));
        List<FakePushSender.SentData> sent = pushSender.sentData();
        assertThat(sent).hasSize(2);
        assertThat(sent.get(1).push().type()).isEqualTo("location_precise_off");
        assertThat(sent.get(1).token()).isEqualTo("partner-tok-" + partnerId);
    }

    @Test
    void 만료되면_자동_비활성() throws Exception {
        toggle(partner, true);
        mvc.perform(post("/api/v1/location/precise").header("Authorization", me)).andExpect(status().isNoContent());
        clock.advance(Duration.ofMinutes(3).plusSeconds(1));

        mvc.perform(get("/api/v1/location/partner").header("Authorization", me))
            .andExpect(jsonPath("$.preciseActive").value(false));
    }

    @Test
    void 자기자신엔_안보내고_토큰없어도_실패안함() throws Exception {
        pushTokenRepository.deleteAll(pushTokenRepository.findByUserIdIn(List.of(partnerId)));

        mvc.perform(post("/api/v1/location/precise").header("Authorization", me))
            .andExpect(status().isNoContent());

        assertThat(pushSender.sentData()).isEmpty();
        assertThat(pushSender.sentData()).noneMatch(s -> s.token().equals("me-tok-" + meId));
        assertThat(requestRepository.findAll()).hasSize(1);
    }

    @Test
    void 커플이_아니면_거부한다() throws Exception {
        UUID soloId = newUser("솔로");
        String solo = "Bearer " + tokenProvider.generateAccessToken(soloId);

        mvc.perform(post("/api/v1/location/precise").header("Authorization", solo))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("COUPLE_REQUIRED"));
        mvc.perform(delete("/api/v1/location/precise").header("Authorization", solo))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("COUPLE_REQUIRED"));
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
