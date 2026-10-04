package com.ourtoday.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ourtoday.api.auth.JwtTokenProvider;
import com.ourtoday.api.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 로그인 → 프로필 → 초대 → 합류 → 내 커플 조회까지 HTTP 레벨 흐름 + 가드/에러 형식
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OnboardingFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserService userService;
    @Autowired JwtTokenProvider tokenProvider;

    @Test
    void 토큰_없으면_401_표준_에러() throws Exception {
        mvc.perform(get("/api/v1/users/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void 프로필_전에는_초대_불가_PROFILE_REQUIRED() throws Exception {
        String token = newUserToken();

        mvc.perform(get("/api/v1/users/me/onboarding-status").header("Authorization", token))
            .andExpect(jsonPath("$.status").value("NEED_PROFILE"));
        mvc.perform(post("/api/v1/couples/invite").header("Authorization", token))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("PROFILE_REQUIRED"));
    }

    @Test
    void 닉네임_비우면_VALIDATION_FAILED_와_필드_상세() throws Exception {
        String token = newUserToken();

        mvc.perform(patch("/api/v1/users/me").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\" \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.error.details.nickname").exists());
    }

    @Test
    void 전체_온보딩_흐름() throws Exception {
        String a = newUserToken();
        String b = newUserToken();
        completeProfile(a, "민지");
        completeProfile(b, "지호");

        mvc.perform(get("/api/v1/users/me/onboarding-status").header("Authorization", a))
            .andExpect(jsonPath("$.status").value("NO_COUPLE"));
        mvc.perform(get("/api/v1/couples/me").header("Authorization", a))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("COUPLE_REQUIRED"));

        String inviteBody = mvc.perform(post("/api/v1/couples/invite").header("Authorization", a))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        String code = objectMapper.readTree(inviteBody).get("inviteCode").asText();

        mvc.perform(get("/api/v1/users/me/onboarding-status").header("Authorization", a))
            .andExpect(jsonPath("$.status").value("PENDING_INVITE"));

        mvc.perform(post("/api/v1/couples/join").header("Authorization", b)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"inviteCode\":\"" + code + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CONNECTED"))
            .andExpect(jsonPath("$.members.length()").value(2));

        mvc.perform(get("/api/v1/couples/me").header("Authorization", a))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.members[0].nickname").value("민지"))
            .andExpect(jsonPath("$.members[1].nickname").value("지호"));
        mvc.perform(get("/api/v1/users/me").header("Authorization", b))
            .andExpect(jsonPath("$.hasCouple").value(true))
            .andExpect(jsonPath("$.profileCompleted").value(true));
        mvc.perform(get("/api/v1/users/me/onboarding-status").header("Authorization", b))
            .andExpect(jsonPath("$.status").value("CONNECTED"));
    }

    private String newUserToken() {
        UUID id = userService.findOrCreateByKakaoId("kakao-" + UUID.randomUUID()).getId();
        return "Bearer " + tokenProvider.generateAccessToken(id);
    }

    private void completeProfile(String token, String nickname) throws Exception {
        String body = mvc.perform(patch("/api/v1/users/me").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"" + nickname + "\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        assert json.get("profileCompleted").asBoolean();
    }
}
