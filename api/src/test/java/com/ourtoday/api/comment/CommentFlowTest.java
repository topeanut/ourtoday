package com.ourtoday.api.comment;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CommentFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserService userService;
    @Autowired CoupleService coupleService;
    @Autowired JwtTokenProvider tokenProvider;

    String me;
    String partner;
    String stranger;
    String postId;
    String otherPostId;

    @BeforeEach
    void setUp() throws Exception {
        UUID a = newUser("민지");
        UUID b = newUser("지호");
        coupleService.join(b, coupleService.createInvite(a).inviteCode());
        UUID c = newUser("남");
        coupleService.join(newUser("남2"), coupleService.createInvite(c).inviteCode());
        me = token(a);
        partner = token(b);
        stranger = token(c);

        String body = objectMapper.writeValueAsString(Map.of(
            "content", "댓글 테스트", "postedDate", LocalDate.now(ZoneId.of("Asia/Seoul")).toString()));
        postId = objectMapper.readTree(mvc.perform(post("/api/v1/posts").header("Authorization", me)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andReturn().getResponse().getContentAsString()).get("id").asText();
        otherPostId = objectMapper.readTree(mvc.perform(post("/api/v1/posts").header("Authorization", me)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void 상대가_댓글을_달면_오래된_순으로_조회된다() throws Exception {
        write(partner, "좋았어").andExpect(status().isCreated());
        write(me, "또 가자").andExpect(status().isCreated());

        mvc.perform(get(url()).header("Authorization", me))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].content").value("좋았어"))
            .andExpect(jsonPath("$[0].author.nickname").value("지호"))
            .andExpect(jsonPath("$[1].content").value("또 가자"));
    }

    @Test
    void 다른_커플은_조회도_작성도_못한다() throws Exception {
        mvc.perform(get(url()).header("Authorization", stranger))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        write(stranger, "안녕").andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void 내_댓글만_수정_삭제할_수_있다() throws Exception {
        String id = commentId(write(partner, "좋았어"));

        edit(me, url() + "/" + id, "고침").andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mvc.perform(delete(url() + "/" + id).header("Authorization", me))
            .andExpect(status().isForbidden());

        edit(partner, url() + "/" + id, "고침").andExpect(status().isOk())
            .andExpect(jsonPath("$.content").value("고침"));
        mvc.perform(delete(url() + "/" + id).header("Authorization", partner))
            .andExpect(status().isNoContent());
        mvc.perform(get(url()).header("Authorization", me))
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void 다른_게시글_경로로는_댓글을_수정할_수_없다() throws Exception {
        String id = commentId(write(partner, "좋았어"));

        edit(partner, "/api/v1/posts/" + otherPostId + "/comments/" + id, "고침")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void 빈_댓글과_200자_초과는_거절된다() throws Exception {
        write(me, "  ").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        write(me, "가".repeat(201)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        write(me, "가".repeat(200)).andExpect(status().isCreated());
    }

    private String url() {
        return "/api/v1/posts/" + postId + "/comments";
    }

    private ResultActions write(String token, String content) throws Exception {
        return mvc.perform(post(url()).header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("content", content))));
    }

    private ResultActions edit(String token, String path, String content) throws Exception {
        return mvc.perform(patch(path).header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("content", content))));
    }

    private String commentId(ResultActions created) throws Exception {
        return objectMapper.readTree(created.andReturn().getResponse().getContentAsString()).get("id").asText();
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
