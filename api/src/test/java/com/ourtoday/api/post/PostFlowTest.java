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
class PostFlowTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));

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
        UUID d = newUser("남2");
        coupleService.join(d, coupleService.createInvite(c).inviteCode());
        me = token(a);
        partner = token(b);
        stranger = token(c);
    }

    @Test
    void 커플_전에는_COUPLE_REQUIRED() throws Exception {
        String solo = token(newUser("솔로"));

        mvc.perform(get("/api/v1/posts").header("Authorization", solo))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("COUPLE_REQUIRED"));
    }

    @Test
    void 작성하면_201과_작성자_정보() throws Exception {
        write(me, "  오늘 처음 같이 본 영화  ", TODAY)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.content").value("오늘 처음 같이 본 영화"))
            .andExpect(jsonPath("$.author.nickname").value("민지"))
            .andExpect(jsonPath("$.postedDate").value(TODAY.toString()));
    }

    @Test
    void 미래_날짜와_300자_초과는_VALIDATION_FAILED() throws Exception {
        write(me, "내일 일기", TODAY.plusDays(1))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        write(me, "가".repeat(301), TODAY)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details.content").exists());
    }

    @Test
    void 목록은_우리_커플_글만_기본_postedDate_내림차순() throws Exception {
        String oldest = idOf(write(me, "1", TODAY.minusDays(2)));
        String newest = idOf(write(partner, "3", TODAY));
        String middle = idOf(write(me, "2", TODAY.minusDays(1)));
        write(stranger, "남의 글", TODAY);

        mvc.perform(get("/api/v1/posts").header("Authorization", me))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].id", contains(newest, middle, oldest)))
            .andExpect(jsonPath("$.page.totalElements").value(3));
    }

    @Test
    void 정렬은_sort_파라미터로_바꿀_수_있다() throws Exception {
        String first = idOf(write(me, "먼저 썼지만 최근 날짜", TODAY));
        String second = idOf(write(me, "나중에 썼지만 옛날 날짜", TODAY.minusDays(5)));

        mvc.perform(get("/api/v1/posts?sort=postedDate,asc").header("Authorization", me))
            .andExpect(jsonPath("$.content[*].id", contains(second, first)));
        mvc.perform(get("/api/v1/posts?sort=createdAt,desc").header("Authorization", me))
            .andExpect(jsonPath("$.content[*].id", contains(second, first)));
        mvc.perform(get("/api/v1/posts?sort=content,asc").header("Authorization", me))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void author와_기간으로_필터링() throws Exception {
        String mine = idOf(write(me, "내 글", TODAY.minusDays(3)));
        String theirs = idOf(write(partner, "상대 글", TODAY));

        mvc.perform(get("/api/v1/posts?author=me").header("Authorization", me))
            .andExpect(jsonPath("$.content[*].id", contains(mine)));
        mvc.perform(get("/api/v1/posts?author=partner").header("Authorization", me))
            .andExpect(jsonPath("$.content[*].id", contains(theirs)));
        mvc.perform(get("/api/v1/posts?from=" + TODAY.minusDays(1)).header("Authorization", me))
            .andExpect(jsonPath("$.content[*].id", contains(theirs)));
        mvc.perform(get("/api/v1/posts?from=" + TODAY + "&to=" + TODAY.minusDays(1)).header("Authorization", me))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 상대는_읽을_수_있지만_수정_삭제는_FORBIDDEN() throws Exception {
        String id = idOf(write(me, "내 글", TODAY));

        mvc.perform(get("/api/v1/posts/" + id).header("Authorization", partner))
            .andExpect(status().isOk());
        mvc.perform(patch("/api/v1/posts/" + id).header("Authorization", partner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("고쳐볼까", TODAY)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mvc.perform(delete("/api/v1/posts/" + id).header("Authorization", partner))
            .andExpect(status().isForbidden());
    }

    @Test
    void 다른_커플은_상세도_FORBIDDEN_없는_글은_NOT_FOUND() throws Exception {
        String id = idOf(write(me, "내 글", TODAY));

        mvc.perform(get("/api/v1/posts/" + id).header("Authorization", stranger))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/posts/" + UUID.randomUUID()).header("Authorization", me))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/posts/not-a-uuid").header("Authorization", me))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 작성자는_수정하고_삭제할_수_있다() throws Exception {
        String id = idOf(write(me, "처음", TODAY));

        mvc.perform(patch("/api/v1/posts/" + id).header("Authorization", me)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("고침", TODAY.minusDays(1))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").value("고침"))
            .andExpect(jsonPath("$.postedDate").value(TODAY.minusDays(1).toString()));

        mvc.perform(delete("/api/v1/posts/" + id).header("Authorization", me))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/posts/" + id).header("Authorization", me))
            .andExpect(status().isNotFound());
    }

    private ResultActions write(String token, String content, LocalDate postedDate) throws Exception {
        return mvc.perform(post("/api/v1/posts").header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(content, postedDate)));
    }

    private String body(String content, LocalDate postedDate) throws Exception {
        return objectMapper.writeValueAsString(new PostBody(content, postedDate.toString()));
    }

    private record PostBody(String content, String postedDate) {}

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
