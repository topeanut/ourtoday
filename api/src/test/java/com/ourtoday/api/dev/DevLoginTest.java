package com.ourtoday.api.dev;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DevLoginTest {

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles("dev")
    @Transactional
    class 개발_프로필 {

        @Autowired MockMvc mvc;
        @Autowired ObjectMapper objectMapper;

        @Test
        void 이름으로_로그인하면_같은_계정의_토큰이_나온다() throws Exception {
            String first = login("qa-a");
            String second = login("qa-a");

            String id1 = me(first);
            String id2 = me(second);
            org.assertj.core.api.Assertions.assertThat(id1).isEqualTo(id2);
            mvc.perform(get("/api/v1/users/me/onboarding-status").header("Authorization", "Bearer " + first))
                .andExpect(jsonPath("$.status").value("NEED_PROFILE"));
        }

        @Test
        void 이름_형식이_틀리면_400() throws Exception {
            mvc.perform(post("/api/v1/dev/login").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"한글 안됨\"}"))
                .andExpect(status().isBadRequest());
        }

        private String login(String name) throws Exception {
            return objectMapper.readTree(mvc.perform(post("/api/v1/dev/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("accessToken").asText();
        }

        private String me(String token) throws Exception {
            return objectMapper.readTree(mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        }
    }

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    class 기본_프로필 {

        @Autowired MockMvc mvc;

        @Test
        void 임시_로그인_엔드포인트가_없다() throws Exception {
            mvc.perform(post("/api/v1/dev/login").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"qa-a\"}"))
                .andExpect(status().isNotFound());
        }
    }
}
