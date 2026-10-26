package com.ourtoday.api.stats;

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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CoupleStatsFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserService userService;
    @Autowired CoupleService coupleService;
    @Autowired JwtTokenProvider tokenProvider;

    LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
    String me;
    String partner;

    @BeforeEach
    void setUp() {
        UUID a = newUser("민지");
        UUID b = newUser("지호");
        coupleService.join(b, coupleService.createInvite(a).inviteCode());
        me = token(a);
        partner = token(b);
    }

    @Test
    void 아무것도_없으면_0_이고_사귄_날은_1일째() throws Exception {
        mvc.perform(get("/api/v1/couples/me/stats").header("Authorization", me))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.me.totalPosts").value(0))
            .andExpect(jsonPath("$.me.consecutiveDays").value(0))
            .andExpect(jsonPath("$.couple.daysTogether").value(1))
            .andExpect(jsonPath("$.couple.firstPostDate").value(nullValue()));
    }

    @Test
    void 나와_상대를_각각_집계한다() throws Exception {
        List<LocalDate> myDates = List.of(today, today.minusDays(1), today.minusDays(3));
        String myPost = null;
        for (LocalDate d : myDates) {
            myPost = write(me, d);
        }
        write(me, today);
        write(partner, today.minusDays(1));
        write(partner, today.minusDays(2));
        memo(me);
        react(partner, myPost, "HEART");
        react(partner, myPost, "FIRE");

        YearMonth month = YearMonth.from(today);
        long myThisMonth = 1 + myDates.stream().filter(d -> YearMonth.from(d).equals(month)).count();

        mvc.perform(get("/api/v1/couples/me/stats").header("Authorization", me))
            .andExpect(jsonPath("$.me.totalPosts").value(4))
            .andExpect(jsonPath("$.me.thisMonthPosts").value(myThisMonth))
            .andExpect(jsonPath("$.me.consecutiveDays").value(2))
            .andExpect(jsonPath("$.me.totalMemos").value(1))
            .andExpect(jsonPath("$.me.totalReactionsGiven").value(0))
            .andExpect(jsonPath("$.partner.totalPosts").value(2))
            .andExpect(jsonPath("$.partner.consecutiveDays").value(2))
            .andExpect(jsonPath("$.partner.totalReactionsGiven").value(2))
            .andExpect(jsonPath("$.couple.firstPostDate").value(today.minusDays(3).toString()));
    }

    @Test
    void 연속_작성일은_오늘_안_썼으면_어제부터_센다() {
        LocalDate t = LocalDate.of(2026, 10, 26);
        assertThat(CoupleStatsService.consecutiveDays(List.of(), t)).isZero();
        assertThat(CoupleStatsService.consecutiveDays(List.of(t, t.minusDays(1), t.minusDays(3)), t)).isEqualTo(2);
        assertThat(CoupleStatsService.consecutiveDays(List.of(t.minusDays(1), t.minusDays(2)), t)).isEqualTo(2);
        assertThat(CoupleStatsService.consecutiveDays(List.of(t.minusDays(2)), t)).isZero();
    }

    private String write(String token, LocalDate date) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("content", "기록", "postedDate", date.toString()));
        return objectMapper.readTree(mvc.perform(post("/api/v1/posts").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private void memo(String token) throws Exception {
        mvc.perform(post("/api/v1/calendar/memos").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"2026-12-25\",\"title\":\"크리스마스\"}"))
            .andExpect(status().isCreated());
    }

    private void react(String token, String postId, String emoji) throws Exception {
        mvc.perform(post("/api/v1/posts/" + postId + "/reactions").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"emojiType\":\"" + emoji + "\"}"))
            .andExpect(status().isOk());
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
