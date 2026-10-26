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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PostMemoriesFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserService userService;
    @Autowired CoupleService coupleService;
    @Autowired JwtTokenProvider tokenProvider;

    LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
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
    void 지난_연도의_같은_날_글을_연도별로_묶는다() throws Exception {
        assumeFalse(today.getMonthValue() == 2 && today.getDayOfMonth() == 29, "윤년 2/29 는 지난해 같은 날이 없음");
        write(me, today.minusYears(2), "재작년 오늘");
        write(me, today.minusYears(1), "작년 오늘 (나)");
        write(partner, today.minusYears(1), "작년 오늘 (상대)");
        write(me, today.minusYears(1).minusDays(1), "작년 어제");
        write(me, today, "오늘");
        write(stranger, today.minusYears(1), "남의 작년 오늘");

        mvc.perform(get("/api/v1/posts/memories/today").header("Authorization", partner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.today").value(today.toString()))
            .andExpect(jsonPath("$.memories[*].year", contains(today.getYear() - 1, today.getYear() - 2)))
            .andExpect(jsonPath("$.memories[0].posts[*].content", contains("작년 오늘 (나)", "작년 오늘 (상대)")))
            .andExpect(jsonPath("$.memories[1].posts[0].content").value("재작년 오늘"));
    }

    @Test
    void 지난_글이_없으면_빈_목록() throws Exception {
        write(me, today, "오늘");

        mvc.perform(get("/api/v1/posts/memories/today").header("Authorization", me))
            .andExpect(jsonPath("$.memories.length()").value(0));
    }

    private void write(String token, LocalDate date, String content) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("content", content, "postedDate", date.toString()));
        mvc.perform(post("/api/v1/posts").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());
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
