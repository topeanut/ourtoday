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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LocationPointsFlowTest {

    @Autowired MockMvc mvc;
    @Autowired UserService userService;
    @Autowired CoupleService coupleService;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired LocationShareEventRepository eventRepository;
    @Autowired LocationPointRepository pointRepository;

    UUID meId;
    String me;

    @BeforeEach
    void setUp() {
        meId = newUser("민지");
        UUID partnerId = newUser("지호");
        coupleService.join(partnerId, coupleService.createInvite(meId).inviteCode());
        me = "Bearer " + tokenProvider.generateAccessToken(meId);
    }

    @Test
    void 공유_켜면_포인트를_올린다() throws Exception {
        toggle(true);
        Instant now = Instant.now();
        upload(body(point(37.5, 127.0, now.plusSeconds(30), "PRECISE"),
            point(37.6, 127.1, now.plusSeconds(60), "COARSE"))).andExpect(status().isNoContent());
        assertThat(pointRepository.findAll()).hasSize(2);
        assertThat(pointRepository.findTopByUserIdOrderByCapturedAtDesc(meId)).get()
            .extracting(LocationPoint::getSource).isEqualTo(PointSource.COARSE);
    }

    @Test
    void 공유_OFF면_거부() throws Exception {
        upload(body(point(37.5, 127.0, Instant.now(), "PRECISE")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        assertThat(pointRepository.count()).isZero();
    }

    @Test
    void OFF_구간_포인트만_버리고_ON_구간은_저장한다() throws Exception {
        Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2026-01-01T01:00:00Z");
        Instant t3 = Instant.parse("2026-01-01T02:00:00Z");
        eventRepository.save(LocationShareEvent.of(meId, true, t1));
        eventRepository.save(LocationShareEvent.of(meId, false, t2));
        eventRepository.save(LocationShareEvent.of(meId, true, t3));
        toggle(true); // 현재 상태 ON (이벤트는 현재 시각)

        upload(body(
            point(37.1, 127.1, t1.plusSeconds(1800), "PRECISE"),
            point(37.2, 127.2, t2.plusSeconds(1800), "PRECISE"),
            point(37.3, 127.3, t3.plusSeconds(1800), "COARSE")))
            .andExpect(status().isNoContent());

        List<LocationPoint> saved = pointRepository.findAll();
        assertThat(saved).extracting(LocationPoint::getLat).containsExactlyInAnyOrder(37.1, 37.3);
    }

    @Test
    void 범위_밖_좌표_거부() throws Exception {
        toggle(true);
        upload(body(point(100, 127.0, Instant.now(), "PRECISE")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    private String point(double lat, double lng, Instant at, String source) {
        return "{\"lat\":" + lat + ",\"lng\":" + lng + ",\"capturedAt\":\"" + at + "\",\"source\":\"" + source + "\"}";
    }

    private String body(String... points) {
        return "{\"points\":[" + String.join(",", points) + "]}";
    }

    private ResultActions upload(String json) throws Exception {
        return mvc.perform(post("/api/v1/location/points").header("Authorization", me)
            .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private void toggle(boolean enabled) throws Exception {
        mvc.perform(put("/api/v1/location/sharing").header("Authorization", me)
            .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":" + enabled + "}"))
            .andExpect(status().isOk());
    }

    private UUID newUser(String nickname) {
        UUID id = userService.findOrCreateByKakaoId("kakao-" + UUID.randomUUID()).getId();
        userService.updateMe(id, new UserUpdateRequest(nickname, null));
        return id;
    }
}
