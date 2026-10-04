package com.ourtoday.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * springdoc ↔ Spring 버전 불일치 시 /v3/api-docs 가 500 나는 회귀 방지
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiDocsTest {

    @Autowired MockMvc mvc;

    @Test
    void api_docs_가_생성되고_커플_API가_포함된다() throws Exception {
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/couples/invite']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/users/me/onboarding-status']").exists());
    }
}
