package com.ourtoday.api.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 카카오톡 앱 로그인(JS SDK)용 state 준비: 돌려준 state 로 기존 콜백(/login/oauth2/code/kakao)이 요청을 찾을 수 있어야 함
 */
@SpringBootTest
@AutoConfigureMockMvc
class KakaoPrepareTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void 준비한_state_가_세션에_저장되어_콜백에서_찾을_수_있다() throws Exception {
        MockHttpSession session = new MockHttpSession();

        String body = mvc.perform(post("/api/v1/auth/kakao/prepare").session(session))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
            .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        String state = json.get("state").asText();
        assertThat(state).isNotBlank();
        assertThat(json.get("redirectUri").asText()).endsWith("/login/oauth2/code/kakao");

        // 콜백 요청을 흉내 내서 Spring 이 같은 저장소에서 꺼낼 수 있는지 확인
        MockHttpServletRequest callback = new MockHttpServletRequest("GET", "/login/oauth2/code/kakao");
        callback.setSession(session);
        callback.setParameter("state", state);
        callback.setParameter("code", "dummy");
        OAuth2AuthorizationRequest saved = new HttpSessionOAuth2AuthorizationRequestRepository()
            .loadAuthorizationRequest(callback);
        assertThat(saved).isNotNull();
        assertThat(saved.getRedirectUri()).isEqualTo(json.get("redirectUri").asText());
    }
}
