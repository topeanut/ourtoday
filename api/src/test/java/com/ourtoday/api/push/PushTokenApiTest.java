package com.ourtoday.api.push;

import com.ourtoday.api.account.AccountService;
import com.ourtoday.api.auth.JwtTokenProvider;
import com.ourtoday.api.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PushTokenApiTest {

    @Autowired MockMvc mvc;
    @Autowired UserService userService;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired PushTokenRepository repository;
    @Autowired AccountService accountService;

    private UUID newUser() {
        return userService.findOrCreateByKakaoId("kakao-" + UUID.randomUUID()).getId();
    }

    private ResultActions register(UUID userId, String token, String platform) throws Exception {
        return mvc.perform(post("/api/v1/push-tokens")
            .header("Authorization", "Bearer " + tokenProvider.generateAccessToken(userId))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"" + token + "\",\"platform\":\"" + platform + "\"}"));
    }

    private ResultActions remove(UUID userId, String token) throws Exception {
        return mvc.perform(delete("/api/v1/push-tokens")
            .header("Authorization", "Bearer " + tokenProvider.generateAccessToken(userId))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"" + token + "\"}"));
    }

    @Test
    void 토큰을_등록하면_저장된다() throws Exception {
        UUID a = newUser();
        register(a, "tok-1", "android").andExpect(status().isNoContent());

        var all = repository.findByUserIdIn(java.util.List.of(a));
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getToken()).isEqualTo("tok-1");
        assertThat(all.get(0).getPlatform()).isEqualTo(PushPlatform.ANDROID);
    }

    @Test
    void 같은_토큰을_다시_등록해도_한_건이다() throws Exception {
        UUID a = newUser();
        register(a, "tok-2", "android").andExpect(status().isNoContent());
        register(a, "tok-2", "ios").andExpect(status().isNoContent());

        var all = repository.findByUserIdIn(java.util.List.of(a));
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getPlatform()).isEqualTo(PushPlatform.IOS);
    }

    @Test
    void 다른_계정이_같은_토큰을_등록하면_새_계정으로_옮겨진다() throws Exception {
        UUID a = newUser();
        UUID b = newUser();
        register(a, "tok-3", "android").andExpect(status().isNoContent());
        register(b, "tok-3", "android").andExpect(status().isNoContent());

        var all = repository.findByUserIdIn(java.util.List.of(a, b));
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getUserId()).isEqualTo(b);
    }

    @Test
    void 삭제하면_사라지고_남의_토큰은_지우지_않는다() throws Exception {
        UUID a = newUser();
        UUID b = newUser();
        register(a, "tok-4", "android").andExpect(status().isNoContent());

        remove(b, "tok-4").andExpect(status().isNoContent());
        assertThat(repository.findByUserIdIn(java.util.List.of(a))).hasSize(1);

        remove(a, "tok-4").andExpect(status().isNoContent());
        assertThat(repository.findByUserIdIn(java.util.List.of(a))).isEmpty();
    }

    @Test
    void 잘못된_platform_은_400() throws Exception {
        UUID a = newUser();
        register(a, "tok-5", "web").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 탈퇴하면_토큰도_삭제된다() throws Exception {
        UUID a = newUser();
        register(a, "tok-6", "android").andExpect(status().isNoContent());

        accountService.withdraw(a);

        assertThat(repository.findByUserIdIn(java.util.List.of(a))).isEmpty();
    }
}
