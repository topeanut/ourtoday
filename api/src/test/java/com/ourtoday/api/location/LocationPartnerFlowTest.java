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
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LocationPartnerFlowTest {

    @Autowired MockMvc mvc;
    @Autowired UserService userService;
    @Autowired CoupleService coupleService;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired LocationPointRepository pointRepository;

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
    void 상대가_공유하고_포인트있으면_최신점을_준다() throws Exception {
        toggle(partner, true);
        Instant now = Instant.now();
        pointRepository.save(LocationPoint.of(partnerId, 37.1, 127.1, null, now.minusSeconds(60), PointSource.COARSE));
        pointRepository.save(LocationPoint.of(partnerId, 37.5, 127.5, null, now, PointSource.PRECISE));

        mvc.perform(get("/api/v1/location/partner").header("Authorization", me))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.partnerSharing").value(true))
            .andExpect(jsonPath("$.latest.lat").value(37.5))
            .andExpect(jsonPath("$.latest.lng").value(127.5))
            .andExpect(jsonPath("$.capturedAt").exists())
            .andExpect(jsonPath("$.preciseActive").value(false));
    }

    @Test
    void 상대가_공유안하면_null과_false() throws Exception {
        pointRepository.save(LocationPoint.of(partnerId, 37.5, 127.5, null, Instant.now(), PointSource.PRECISE));

        mvc.perform(get("/api/v1/location/partner").header("Authorization", me))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.partnerSharing").value(false))
            .andExpect(jsonPath("$.latest").doesNotExist())
            .andExpect(jsonPath("$.capturedAt").doesNotExist())
            .andExpect(jsonPath("$.preciseActive").value(false));
    }

    @Test
    void 공유를_끄면_상대에게_안_보인다() throws Exception {
        toggle(partner, true);
        pointRepository.save(LocationPoint.of(partnerId, 37.5, 127.5, null, Instant.now(), PointSource.PRECISE));
        toggle(partner, false);

        mvc.perform(get("/api/v1/location/partner").header("Authorization", me))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.partnerSharing").value(false))
            .andExpect(jsonPath("$.latest").doesNotExist())
            .andExpect(jsonPath("$.capturedAt").doesNotExist());
    }

    @Test
    void 커플이_아니면_거부한다() throws Exception {
        UUID soloId = newUser("솔로");
        mvc.perform(get("/api/v1/location/partner")
                .header("Authorization", "Bearer " + tokenProvider.generateAccessToken(soloId)))
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
