package com.ourtoday.api.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ourtoday.api.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TokenRefreshTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserService userService;
    @Autowired JwtTokenProvider tokenProvider;

    @Test
    void 리프레시_토큰으로_새_토큰을_받고_API_를_호출한다() throws Exception {
        UUID userId = userService.findOrCreateByKakaoId("kakao-" + UUID.randomUUID()).getId();
        JsonNode tokens = objectMapper.readTree(refresh(tokenProvider.generateRefreshToken(userId))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());

        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + tokens.get("accessToken").asText()))
            .andExpect(status().isOk());
        refresh(tokens.get("refreshToken").asText()).andExpect(status().isOk());
    }

    @Test
    void 리프레시_토큰은_API_호출에_못_쓰고_액세스_토큰은_재발급에_못_쓴다() throws Exception {
        UUID userId = userService.findOrCreateByKakaoId("kakao-" + UUID.randomUUID()).getId();

        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + tokenProvider.generateRefreshToken(userId)))
            .andExpect(status().isUnauthorized());
        refresh(tokenProvider.generateAccessToken(userId)).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        refresh("garbage").andExpect(status().isUnauthorized());
    }

    @Test
    void 없는_사용자의_리프레시_토큰은_401() throws Exception {
        refresh(tokenProvider.generateRefreshToken(UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    private ResultActions refresh(String token) throws Exception {
        return mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
            .content("{\"refreshToken\":\"" + token + "\"}"));
    }
}
