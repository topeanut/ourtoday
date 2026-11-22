package com.ourtoday.api.post;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PostLocationFlowTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserService userService;
    @Autowired CoupleService coupleService;
    @Autowired JwtTokenProvider tokenProvider;

    String me;

    @BeforeEach
    void setUp() {
        UUID a = newUser("민지");
        UUID b = newUser("지호");
        coupleService.join(b, coupleService.createInvite(a).inviteCode());
        me = token(a);
    }

    @Test
    void 위치와_함께_게시글을_만들면_응답에_location_이_담긴다() throws Exception {
        create("""
            {"content":"성수동 데이트","postedDate":"%s","location":{"lat":37.5447,"lng":127.0558,"placeName":"성수동"}}
            """.formatted(TODAY))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.location.placeName").value("성수동"))
            .andExpect(jsonPath("$.location.lat").value(37.5447))
            .andExpect(jsonPath("$.location.lng").value(127.0558));
    }

    @Test
    void 위치_없이_만들_수_있다() throws Exception {
        create("""
            {"content":"집에서","postedDate":"%s"}
            """.formatted(TODAY))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.location").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void 좌표와_장소명은_함께여야_한다() throws Exception {
        create("""
            {"content":"일부만","postedDate":"%s","location":{"lat":37.5}}
            """.formatted(TODAY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        create("""
            {"content":"장소명만","postedDate":"%s","location":{"placeName":"성수동"}}
            """.formatted(TODAY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 범위_밖_좌표는_거부한다() throws Exception {
        create("""
            {"content":"범위밖","postedDate":"%s","location":{"lat":100,"lng":127,"placeName":"어딘가"}}
            """.formatted(TODAY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        create("""
            {"content":"범위밖","postedDate":"%s","location":{"lat":37,"lng":181,"placeName":"어딘가"}}
            """.formatted(TODAY))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 수정으로_위치를_지울_수_있다() throws Exception {
        String id = objectMapper.readTree(create("""
            {"content":"성수동","postedDate":"%s","location":{"lat":37.5447,"lng":127.0558,"placeName":"성수동"}}
            """.formatted(TODAY)).andReturn().getResponse().getContentAsString()).get("id").asText();

        mvc.perform(patch("/api/v1/posts/" + id).header("Authorization", me)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"content":"성수동","postedDate":"%s","location":null}
                    """.formatted(TODAY)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.location").value(org.hamcrest.Matchers.nullValue()));
    }

    private ResultActions create(String json) throws Exception {
        return mvc.perform(post("/api/v1/posts").header("Authorization", me)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
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
