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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LocationSharingFlowTest {

    @Autowired MockMvc mvc;
    @Autowired UserService userService;
    @Autowired CoupleService coupleService;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired LocationShareEventRepository eventRepository;
    @Autowired LocationService locationService;

    UUID meId;
    UUID partnerId;
    String me;
    String partner;
    String solo;

    @BeforeEach
    void setUp() {
        meId = newUser("민지");
        partnerId = newUser("지호");
        coupleService.join(partnerId, coupleService.createInvite(meId).inviteCode());
        me = token(meId);
        partner = token(partnerId);
        solo = token(newUser("솔로"));
    }

    @Test
    void 공유를_켜고_끌_수_있다() throws Exception {
        toggle(me, true).andExpect(status().isOk())
            .andExpect(jsonPath("$.mine").value(true));
        mvc.perform(get("/api/v1/location/sharing").header("Authorization", me))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mine").value(true));
        toggle(me, false).andExpect(jsonPath("$.mine").value(false));
        mvc.perform(get("/api/v1/location/sharing").header("Authorization", me))
            .andExpect(jsonPath("$.mine").value(false));
    }

    @Test
    void 상대_공유_상태가_보인다() throws Exception {
        toggle(partner, true);
        mvc.perform(get("/api/v1/location/sharing").header("Authorization", me))
            .andExpect(jsonPath("$.mine").value(false))
            .andExpect(jsonPath("$.partner").value(true));
    }

    @Test
    void 공유를_끄면_상대에게_안_보인다() throws Exception {
        toggle(partner, true);
        toggle(partner, false);
        mvc.perform(get("/api/v1/location/sharing").header("Authorization", me))
            .andExpect(jsonPath("$.partner").value(false));
    }

    @Test
    void 토글하면_이벤트가_쌓인다() throws Exception {
        toggle(me, false);
        assertThat(eventRepository.count()).isZero();
        toggle(me, true);
        toggle(me, true);
        toggle(me, false);
        toggle(me, false);
        toggle(me, true);
        assertThat(eventRepository.count()).isEqualTo(3);
        assertThat(locationService.isSharing(meId)).isTrue();
        assertThat(locationService.wasSharingAt(meId, Instant.now().plusSeconds(60))).isTrue();
        assertThat(locationService.wasSharingAt(meId, Instant.EPOCH)).isFalse();
    }

    @Test
    void 커플이_아니면_거부한다() throws Exception {
        toggle(solo, true).andExpect(jsonPath("$.error.code").value("COUPLE_REQUIRED"));
        mvc.perform(get("/api/v1/location/sharing").header("Authorization", solo))
            .andExpect(jsonPath("$.error.code").value("COUPLE_REQUIRED"));
    }

    private ResultActions toggle(String token, boolean enabled) throws Exception {
        return mvc.perform(put("/api/v1/location/sharing").header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":" + enabled + "}"));
    }

    private UUID newUser(String nickname) {
        UUID id = userService.findOrCreateByKakaoId("kakao-" + UUID.randomUUID()).getId();
        userService.updateMe(id, new UserUpdateRequest(nickname, null));
        return id;
    }

    private String token(UUID userId) {
        return "Bearer " + tokenProvider.generateAccessToken(userId);
    }
}
