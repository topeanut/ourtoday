package com.ourtoday.api.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ourtoday.api.auth.apple.AppleAuthClient;
import com.ourtoday.api.auth.apple.AppleIdentity;
import com.ourtoday.api.auth.apple.AppleIdentityVerifier;
import com.ourtoday.api.user.User;
import com.ourtoday.api.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 트랜잭션 없이 실행 — 탈퇴 후 커밋 시점의 Apple 철회까지 확인하려고 */
@SpringBootTest
@AutoConfigureMockMvc
class AppleLoginTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @MockitoBean AppleIdentityVerifier verifier;
    @MockitoBean AppleAuthClient appleAuthClient;

    @Test
    void 처음_로그인하면_가입되고_다시_로그인하면_같은_계정이다() throws Exception {
        String sub = "001234." + UUID.randomUUID();
        given(verifier.verify("id-token")).willReturn(new AppleIdentity(sub, "app.ourtoday"));
        given(appleAuthClient.exchangeCode("code-1", "app.ourtoday")).willReturn("apple-refresh-1");

        String access = token(login("id-token", "code-1").andExpect(status().isOk()));
        JsonNode me = objectMapper.readTree(mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + access))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(me.get("profileCompleted").asBoolean()).isFalse();

        String again = token(login("id-token", null).andExpect(status().isOk()));
        JsonNode me2 = objectMapper.readTree(mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + again))
            .andReturn().getResponse().getContentAsString());
        assertThat(me2.get("id").asText()).isEqualTo(me.get("id").asText());

        // 코드 없이 재로그인해도 기존 refresh token 은 유지
        User user = userRepository.findByKakaoId("apple:" + sub).orElseThrow();
        assertThat(user.getAppleRefreshToken()).isEqualTo("apple-refresh-1");
        assertThat(user.getAppleClientId()).isEqualTo("app.ourtoday");
    }

    @Test
    void 검증에_실패한_토큰은_401() throws Exception {
        given(verifier.verify(anyString())).willThrow(new IllegalArgumentException("bad signature"));

        login("forged", null).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void 토큰이_비어_있으면_400() throws Exception {
        mvc.perform(post("/api/v1/auth/apple").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 탈퇴하면_Apple_토큰을_철회한다() throws Exception {
        String sub = "009999." + UUID.randomUUID();
        given(verifier.verify("id-token-w")).willReturn(new AppleIdentity(sub, "app.ourtoday.web"));
        given(appleAuthClient.exchangeCode(any(), any())).willReturn("apple-refresh-w");
        String access = token(login("id-token-w", "code-w").andExpect(status().isOk()));

        mvc.perform(delete("/api/v1/users/me").header("Authorization", "Bearer " + access))
            .andExpect(status().isNoContent());

        verify(appleAuthClient).revoke("apple-refresh-w", "app.ourtoday.web");
        assertThat(userRepository.findByKakaoId("apple:" + sub)).isEmpty();
    }

    private ResultActions login(String identityToken, String code) throws Exception {
        String body = objectMapper.writeValueAsString(
            code == null ? java.util.Map.of("identityToken", identityToken)
                : java.util.Map.of("identityToken", identityToken, "authorizationCode", code));
        return mvc.perform(post("/api/v1/auth/apple").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String token(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("accessToken").asText();
    }
}
