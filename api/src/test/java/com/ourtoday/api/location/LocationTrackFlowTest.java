package com.ourtoday.api.location;

import com.ourtoday.api.auth.JwtTokenProvider;
import com.ourtoday.api.couple.CoupleService;
import com.ourtoday.api.user.UserService;
import com.ourtoday.api.user.dto.UserUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LocationTrackFlowTest {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final LocalDate DAY = LocalDate.of(2026, 11, 20);

    @Autowired MockMvc mvc;
    @Autowired UserService userService;
    @Autowired CoupleService coupleService;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired LocationPointRepository pointRepository;
    @Autowired LocationDailyRepository dailyRepository;
    @Autowired DailyAggregator aggregator;

    UUID meId;
    UUID partnerId;
    String me;
    String partner;

    @BeforeEach
    void setUp() {
        meId = newUser("민지");
        partnerId = newUser("지호");
        coupleService.join(partnerId, coupleService.createInvite(meId).inviteCode());
        me = "Bearer " + tokenProvider.generateAccessToken(meId);
        partner = "Bearer " + tokenProvider.generateAccessToken(partnerId);
    }

    @Test
    void 요약이_있으면_polyline을_준다() throws Exception {
        toggle(partner, true);
        Instant at = DAY.atTime(10, 0).atZone(KST).toInstant();
        dailyRepository.save(LocationDaily.of(partnerId, DAY, 50, at, at.plusSeconds(3600), "37.1,127.1;37.2,127.2"));
        pointRepository.save(LocationPoint.of(partnerId, 1, 1, null, at, PointSource.COARSE));

        mvc.perform(get("/api/v1/location/track").param("date", "2026-11-20").header("Authorization", me))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pointCount").value(50))
            .andExpect(jsonPath("$.path.length()").value(2))
            .andExpect(jsonPath("$.path[0].lat").value(37.1))
            .andExpect(jsonPath("$.path[1].lng").value(127.2));
    }

    @Test
    void 요약이_없으면_raw를_준다() throws Exception {
        toggle(partner, true);
        // KST 경계: 11-19 23:59 (제외) / 11-20 00:00 (포함) / 11-20 23:59 (포함) / 11-21 00:00 (제외)
        save(partnerId, 9.0, 9.0, DAY.minusDays(1).atTime(23, 59));
        save(partnerId, 37.1, 127.1, DAY.atTime(0, 0));
        save(partnerId, 37.2, 127.2, DAY.atTime(23, 59));
        save(partnerId, 8.0, 8.0, DAY.plusDays(1).atTime(0, 0));
        save(meId, 7.0, 7.0, DAY.atTime(12, 0));

        mvc.perform(get("/api/v1/location/track").param("date", "2026-11-20").header("Authorization", me))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pointCount").value(2))
            .andExpect(jsonPath("$.path[0].lat").value(37.1))
            .andExpect(jsonPath("$.path[1].lat").value(37.2));
    }

    @Test
    void 상대_공유여부_검증() throws Exception {
        save(partnerId, 37.1, 127.1, DAY.atTime(10, 0));

        mvc.perform(get("/api/v1/location/track").param("date", "2026-11-20").header("Authorization", me))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pointCount").value(0))
            .andExpect(jsonPath("$.path.length()").value(0));
    }

    @Test
    void 전일_포인트를_요약으로_만든다() {
        save(partnerId, 37.0, 127.0, DAY.atTime(9, 0));
        save(partnerId, 37.0005, 127.0005, DAY.atTime(9, 5));
        save(partnerId, 37.001, 127.001, DAY.atTime(9, 10));
        save(partnerId, 40.0, 130.0, DAY.plusDays(1).atTime(1, 0));

        aggregator.aggregate(DAY);

        LocationDaily d = dailyRepository.findByUserIdAndDate(partnerId, DAY).orElseThrow();
        assertThat(d.getPointCount()).isEqualTo(3);
        assertThat(d.getFirstAt()).isEqualTo(DAY.atTime(9, 0).atZone(KST).toInstant());
        assertThat(d.getLastAt()).isEqualTo(DAY.atTime(9, 10).atZone(KST).toInstant());
        assertThat(d.getPolyline()).isEqualTo("37.0,127.0;37.001,127.001");

        aggregator.aggregate(DAY); // 재실행은 upsert
        assertThat(dailyRepository.findAll().stream().filter(x -> x.getUserId().equals(partnerId))).hasSize(1);
    }

    @Test
    void 점이_없으면_요약_안만든다() {
        aggregator.aggregate(DAY);
        assertThat(dailyRepository.findByUserIdAndDate(partnerId, DAY)).isEmpty();
        assertThat(dailyRepository.findByUserIdAndDate(meId, DAY)).isEmpty();
    }

    private void save(UUID userId, double lat, double lng, java.time.LocalDateTime kst) {
        pointRepository.save(LocationPoint.of(userId, lat, lng, null, kst.atZone(KST).toInstant(), PointSource.COARSE));
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
