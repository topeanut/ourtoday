package com.ourtoday.api.calendar;

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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CalendarMemoFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserService userService;
    @Autowired CoupleService coupleService;
    @Autowired JwtTokenProvider tokenProvider;

    String me;
    String partner;
    String stranger;

    @BeforeEach
    void setUp() {
        UUID a = newUser("민지");
        UUID b = newUser("지호");
        coupleService.join(b, coupleService.createInvite(a).inviteCode());
        UUID c = newUser("남");
        coupleService.join(newUser("남2"), coupleService.createInvite(c).inviteCode());
        me = token(a);
        partner = token(b);
        stranger = token(c);
    }

    @Test
    void 기간_조회는_날짜순이고_상대_메모도_보인다() throws Exception {
        create(me, "2026-06-14", "1주년", "#ffb6c1").andExpect(status().isCreated())
            .andExpect(jsonPath("$.color").value("#FFB6C1"))
            .andExpect(jsonPath("$.createdAt").isNotEmpty())
            .andExpect(jsonPath("$.updatedAt").isNotEmpty())
            .andExpect(jsonPath("$.author.nickname").value("민지"));
        create(partner, "2026-06-01", "영화", null);
        create(me, "2026-07-01", "범위 밖", null);

        mvc.perform(get("/api/v1/calendar/memos?from=2026-06-01&to=2026-06-30").header("Authorization", me))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].title", contains("영화", "1주년")))
            .andExpect(jsonPath("$.items[0].author.nickname").value("지호"));

        mvc.perform(get("/api/v1/calendar/memos?from=2026-06-01&to=2026-06-30").header("Authorization", stranger))
            .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void 수정_삭제는_작성자만() throws Exception {
        ResultActions created = create(me, "2026-06-14", "1주년", null);
        String id = idOf(created);
        String updatedBefore = objectMapper.readTree(created.andReturn().getResponse().getContentAsString())
            .get("updatedAt").asText();
        String body = "{\"date\":\"2026-06-15\",\"title\":\"하루 미룸\"}";

        mvc.perform(patch("/api/v1/calendar/memos/" + id).header("Authorization", partner)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/calendar/memos/" + id).header("Authorization", me)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.date").value("2026-06-15"))
            .andExpect(jsonPath("$.title").value("하루 미룸"))
            .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                objectMapper.readTree(result.getResponse().getContentAsString()).get("updatedAt").asText())
                .isGreaterThan(updatedBefore));

        mvc.perform(delete("/api/v1/calendar/memos/" + id).header("Authorization", partner))
            .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/calendar/memos/" + id).header("Authorization", me))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/calendar/memos/" + id).header("Authorization", me))
            .andExpect(status().isNotFound());
    }

    @Test
    void 다른_커플_메모_상세는_FORBIDDEN() throws Exception {
        String id = idOf(create(me, "2026-06-14", "1주년", null));

        mvc.perform(get("/api/v1/calendar/memos/" + id).header("Authorization", stranger))
            .andExpect(status().isForbidden());
    }

    @Test
    void 잘못된_입력은_VALIDATION_FAILED() throws Exception {
        create(me, "2026-06-14", "x".repeat(51), null).andExpect(status().isBadRequest());
        create(me, "2026-06-14", "색 이상", "pink").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details.color").exists());

        mvc.perform(get("/api/v1/calendar/memos?from=2026-06-30&to=2026-06-01").header("Authorization", me))
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/api/v1/calendar/memos?from=2026-01-01&to=2026-12-31").header("Authorization", me))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/calendar/memos?from=2026-06-01").header("Authorization", me))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details.to").exists());
    }

    private ResultActions create(String token, String date, String title, String color) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("date", date);
        body.put("title", title);
        body.put("color", color);
        return mvc.perform(post("/api/v1/calendar/memos").header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private String idOf(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asText();
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
