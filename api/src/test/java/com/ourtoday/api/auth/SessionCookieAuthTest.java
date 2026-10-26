package com.ourtoday.api.auth;

import com.ourtoday.api.user.UserService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * JWT 로 한 번 인증한 뒤 세션 쿠키만으로 다시 인증되던 문제(2026-10-26 QA) 회귀 방지
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SessionCookieAuthTest {

    @Autowired MockMvc mvc;
    @Autowired UserService userService;
    @Autowired JwtTokenProvider tokenProvider;

    @Test
    void 토큰_없이_세션만으로는_인증되지_않는다() throws Exception {
        UUID userId = userService.findOrCreateByKakaoId("kakao-" + UUID.randomUUID()).getId();
        MockHttpSession session = new MockHttpSession();

        mvc.perform(get("/api/v1/users/me").session(session)
                .header("Authorization", "Bearer " + tokenProvider.generateAccessToken(userId)))
            .andExpect(status().isOk());

        HttpSession same = session;
        mvc.perform(get("/api/v1/users/me").session((MockHttpSession) same))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users/me").session((MockHttpSession) same).header("Authorization", "Bearer garbage"))
            .andExpect(status().isUnauthorized());
    }
}
