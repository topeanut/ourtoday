package com.ourtoday.api.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ourtoday.api.auth.JwtTokenProvider;
import com.ourtoday.api.couple.CoupleService;
import com.ourtoday.api.image.FakeImageStorage;
import com.ourtoday.api.image.TestStorageConfig;
import com.ourtoday.api.user.dto.UserUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestStorageConfig.class)
class ProfileImageFlowTest {

    private static final String KAKAO_URL = "https://k.kakaocdn.net/dn/profile.jpg";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserService userService;
    @Autowired CoupleService coupleService;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired FakeImageStorage storage;

    UUID meId;
    String me;

    @BeforeEach
    void setUp() {
        // 가입 중(닉네임 입력 전, 커플 없음) 상태
        meId = userService.findOrCreateByKakaoId("kakao-" + UUID.randomUUID()).getId();
        me = token(meId);
    }

    @Test
    void 가입_중_커플_없이도_사진을_올리고_프로필로_지정할_수_있다() throws Exception {
        String key = uploaded(me);
        assertThat(key).startsWith("users/" + meId + "/profile/");

        patchMe(me, "민지", key).andExpect(status().isOk())
            .andExpect(jsonPath("$.profileCompleted").value(true))
            .andExpect(jsonPath("$.hasCustomProfileImage").value(true))
            .andExpect(jsonPath("$.profileImageUrl").value("https://fake-storage/download/" + key));
    }

    @Test
    void 남의_키나_올리지_않은_키는_거절된다() throws Exception {
        UUID other = userService.findOrCreateByKakaoId("kakao-" + UUID.randomUUID()).getId();
        String othersKey = uploaded(token(other));
        patchMe(me, "민지", othersKey).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

        String notUploaded = presignKey(me);
        patchMe(me, "민지", notUploaded).andExpect(status().isBadRequest());
    }

    @Test
    void 직접_올린_사진이_카카오_사진보다_우선하고_지우면_카카오로_돌아간다() throws Exception {
        userService.syncKakaoProfile(meId, KAKAO_URL, null);
        patchMe(me, "민지", null)
            .andExpect(jsonPath("$.profileImageUrl").value(KAKAO_URL))
            .andExpect(jsonPath("$.hasCustomProfileImage").value(false));

        String key = uploaded(me);
        patchMe(me, "민지", key)
            .andExpect(jsonPath("$.profileImageUrl").value("https://fake-storage/download/" + key));

        // 다시 카카오로 로그인해도 직접 올린 사진이 유지됨
        userService.syncKakaoProfile(meId, KAKAO_URL, null);
        mvc.perform(get("/api/v1/users/me").header("Authorization", me))
            .andExpect(jsonPath("$.profileImageUrl").value("https://fake-storage/download/" + key));

        mvc.perform(delete("/api/v1/users/me/profile-image").header("Authorization", me))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.profileImageUrl").value(KAKAO_URL))
            .andExpect(jsonPath("$.hasCustomProfileImage").value(false));
        assertThat(storage.exists(key)).isFalse();
    }

    @Test
    void 닉네임만_바꾸면_사진은_그대로고_사진을_바꾸면_이전_파일은_지워진다() throws Exception {
        String first = uploaded(me);
        patchMe(me, "민지", first);
        patchMe(me, "민지2", null)
            .andExpect(jsonPath("$.profileImageUrl").value("https://fake-storage/download/" + first));

        String second = uploaded(me);
        patchMe(me, "민지2", second)
            .andExpect(jsonPath("$.profileImageUrl").value("https://fake-storage/download/" + second));
        assertThat(storage.exists(first)).isFalse();
        assertThat(storage.exists(second)).isTrue();
    }

    @Test
    void 게시글과_커플_정보의_작성자_사진에도_반영된다() throws Exception {
        String key = uploaded(me);
        patchMe(me, "민지", key);
        UUID partner = userService.findOrCreateByKakaoId("kakao-" + UUID.randomUUID()).getId();
        userService.updateMe(partner, new UserUpdateRequest("지호", null));
        userService.syncKakaoProfile(partner, KAKAO_URL, null);
        coupleService.join(partner, coupleService.createInvite(meId).inviteCode());

        String body = objectMapper.writeValueAsString(Map.of(
            "content", "사진 테스트", "postedDate", LocalDate.now(ZoneId.of("Asia/Seoul")).toString()));
        mvc.perform(post("/api/v1/posts").header("Authorization", token(partner))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(jsonPath("$.author.profileImageUrl").value(KAKAO_URL));

        mvc.perform(get("/api/v1/couples/me").header("Authorization", me))
            .andExpect(jsonPath("$.members[0].profileImageUrl").value("https://fake-storage/download/" + key));
    }

    @Test
    void 탈퇴하면_올린_프로필_사진도_지워진다() throws Exception {
        String key = uploaded(me);
        patchMe(me, "민지", key);
        mvc.perform(delete("/api/v1/users/me").header("Authorization", me)).andExpect(status().isNoContent());
        assertThat(storage.exists(key)).isFalse();
    }

    @Test
    void 가입_전에는_카카오_닉네임이_기본값으로_들어가고_가입_후에는_덮어쓰지_않는다() throws Exception {
        userService.syncKakaoProfile(meId, null, "카카오민지");
        mvc.perform(get("/api/v1/users/me").header("Authorization", me))
            .andExpect(jsonPath("$.nickname").value("카카오민지"))
            .andExpect(jsonPath("$.profileCompleted").value(false));

        patchMe(me, "민지", null).andExpect(jsonPath("$.nickname").value("민지"));
        userService.syncKakaoProfile(meId, null, "카카오민지");
        mvc.perform(get("/api/v1/users/me").header("Authorization", me))
            .andExpect(jsonPath("$.nickname").value("민지"));
    }

    private ResultActions patchMe(String token, String nickname, String profileImageKey) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("nickname", nickname);
        body.put("profileImageKey", profileImageKey);
        return mvc.perform(patch("/api/v1/users/me").header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private String presignKey(String token) throws Exception {
        String res = mvc.perform(post("/api/v1/users/me/profile-image/presigned-url").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\":\"image/jpeg\",\"contentLength\":1000}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.uploadUrl", startsWith("https://fake-storage/upload/users/")))
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(res).get("imageKey").asText();
    }

    private String uploaded(String token) throws Exception {
        String key = presignKey(token);
        storage.upload(key, 1000);
        return key;
    }

    private String token(UUID userId) {
        return "Bearer " + tokenProvider.generateAccessToken(userId);
    }
}
