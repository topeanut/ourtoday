package com.ourtoday.api.bucket;

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
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BucketItemFlowTest {

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
    void 상대가_추가한_항목도_수정_완료_삭제할_수_있다() throws Exception {
        String id = idOf(create(me, "오로라 보러 가기")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.completed").value(false))
            .andExpect(jsonPath("$.createdBy.nickname").value("민지")));

        mvc.perform(patch("/api/v1/bucket-items/" + id).header("Authorization", partner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"오로라\",\"targetDate\":\"2027-01-01\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("오로라"))
            .andExpect(jsonPath("$.targetDate").value("2027-01-01"));

        mvc.perform(patch("/api/v1/bucket-items/" + id + "/complete").header("Authorization", partner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.completed").value(true))
            .andExpect(jsonPath("$.completedAt").exists());

        mvc.perform(delete("/api/v1/bucket-items/" + id + "/complete").header("Authorization", me))
            .andExpect(jsonPath("$.completed").value(false))
            .andExpect(jsonPath("$.completedAt").value(nullValue()));

        mvc.perform(delete("/api/v1/bucket-items/" + id).header("Authorization", partner))
            .andExpect(status().isNoContent());
    }

    @Test
    void 상태별_목록() throws Exception {
        create(me, "첫째");
        String done = idOf(create(me, "둘째"));
        create(partner, "셋째");
        mvc.perform(patch("/api/v1/bucket-items/" + done + "/complete").header("Authorization", me));

        mvc.perform(get("/api/v1/bucket-items").header("Authorization", me))
            .andExpect(jsonPath("$.items[*].title", contains("셋째", "첫째", "둘째")));
        mvc.perform(get("/api/v1/bucket-items?status=TODO").header("Authorization", me))
            .andExpect(jsonPath("$.items[*].title", contains("셋째", "첫째")));
        mvc.perform(get("/api/v1/bucket-items?status=DONE").header("Authorization", partner))
            .andExpect(jsonPath("$.items[*].title", contains("둘째")));
        mvc.perform(get("/api/v1/bucket-items?status=WRONG").header("Authorization", me))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/bucket-items").header("Authorization", stranger))
            .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void 완료하며_게시글을_연결하고_글이_지워지면_연결만_끊긴다() throws Exception {
        String id = idOf(create(me, "한강 피크닉"));
        String postId = createPost(partner);

        complete(me, id, postId).andExpect(status().isOk())
            .andExpect(jsonPath("$.completedPostId").value(postId));

        mvc.perform(delete("/api/v1/posts/" + postId).header("Authorization", partner))
            .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/bucket-items").header("Authorization", me))
            .andExpect(jsonPath("$.items[0].completed").value(true))
            .andExpect(jsonPath("$.items[0].completedPostId").value(nullValue()));
    }

    @Test
    void 다른_커플_항목이나_글은_FORBIDDEN() throws Exception {
        String id = idOf(create(me, "우리 것"));
        String strangerPost = createPost(stranger);

        mvc.perform(delete("/api/v1/bucket-items/" + id).header("Authorization", stranger))
            .andExpect(status().isForbidden());
        complete(me, id, strangerPost).andExpect(status().isForbidden());
        complete(me, id, UUID.randomUUID().toString()).andExpect(status().isNotFound());
    }

    @Test
    void 제목이_없으면_VALIDATION_FAILED() throws Exception {
        create(me, " ").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    private ResultActions create(String token, String title) throws Exception {
        return mvc.perform(post("/api/v1/bucket-items").header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("title", title))));
    }

    private ResultActions complete(String token, String id, String postId) throws Exception {
        return mvc.perform(patch("/api/v1/bucket-items/" + id + "/complete").header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON).content("{\"postId\":\"" + postId + "\"}"));
    }

    private String createPost(String token) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
            "content", "달성!", "postedDate", LocalDate.now(ZoneId.of("Asia/Seoul")).toString()));
        return idOf(mvc.perform(post("/api/v1/posts").header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON).content(body)));
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
