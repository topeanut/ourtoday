package com.ourtoday.api.reaction;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReactionFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserService userService;
    @Autowired CoupleService coupleService;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired ReactionRepository reactionRepository;

    String me;
    String partner;
    String stranger;
    String postId;

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
            "content", "반응 테스트", "postedDate", LocalDate.now(ZoneId.of("Asia/Seoul")).toString()));
        postId = objectMapper.readTree(mvc.perform(post("/api/v1/posts").header("Authorization", me)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void 같은_이모지를_다시_누르면_취소된다() throws Exception {
        toggle(partner, "HEART").andExpect(status().isOk())
            .andExpect(jsonPath("$.reacted").value(true))
            .andExpect(jsonPath("$.emojiType").value("HEART"));
        toggle(partner, "HEART")
            .andExpect(jsonPath("$.reacted").value(false));

        mvc.perform(get("/api/v1/posts/" + postId).header("Authorization", me))
            .andExpect(jsonPath("$.reactions.length()").value(0));
    }

    @Test
    void 개수와_내가_눌렀는지가_보는_사람_기준으로_집계된다() throws Exception {
        toggle(me, "HEART");
        toggle(partner, "HEART");
        toggle(partner, "FIRE");
        toggle(me, "LAUGH");

        mvc.perform(get("/api/v1/posts/" + postId).header("Authorization", partner))
            .andExpect(jsonPath("$.reactions[*].emojiType", contains("HEART", "LAUGH", "FIRE")))
            .andExpect(jsonPath("$.reactions[0].count").value(2))
            .andExpect(jsonPath("$.reactions[0].reactedByMe").value(true))
            .andExpect(jsonPath("$.reactions[1].reactedByMe").value(false))
            .andExpect(jsonPath("$.reactions[2].reactedByMe").value(true));

        mvc.perform(get("/api/v1/posts").header("Authorization", me))
            .andExpect(jsonPath("$.content[0].reactions[0].count").value(2))
            .andExpect(jsonPath("$.content[0].reactions[2].reactedByMe").value(false));
    }

    @Test
    void 다른_커플_글에는_반응할_수_없다() throws Exception {
        toggle(stranger, "HEART").andExpect(status().isForbidden());
    }

    @Test
    void 없는_이모지는_VALIDATION_FAILED() throws Exception {
        toggle(me, "ANGRY").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 글을_지우면_반응도_지워진다() throws Exception {
        toggle(partner, "HEART");

        mvc.perform(delete("/api/v1/posts/" + postId).header("Authorization", me))
            .andExpect(status().isNoContent());

        org.assertj.core.api.Assertions.assertThat(reactionRepository.count()).isZero();
    }

    private ResultActions toggle(String token, String emoji) throws Exception {
        return mvc.perform(post("/api/v1/posts/" + postId + "/reactions").header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"emojiType\":\"" + emoji + "\"}"));
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
