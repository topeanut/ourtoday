package com.ourtoday.api.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ourtoday.api.auth.JwtTokenProvider;
import com.ourtoday.api.couple.CoupleService;
import com.ourtoday.api.image.FakeImageStorage;
import com.ourtoday.api.image.ImageService;
import com.ourtoday.api.image.TestStorageConfig;
import com.ourtoday.api.user.UserService;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 커밋 후 파일 삭제(afterCommit)를 확인해야 해서 @Transactional 없이 실행 (데이터는 유저마다 새로 생성)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestStorageConfig.class)
class PostImageFlowTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserService userService;
    @Autowired CoupleService coupleService;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired FakeImageStorage storage;

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
    void 업로드_URL_발급() throws Exception {
        presign(me, "image/jpeg", 2_000_000)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.imageKey", startsWith("couples/")))
            .andExpect(jsonPath("$.uploadUrl", startsWith("https://fake-storage/upload/")))
            .andExpect(jsonPath("$.expiresInSec").value(600));
    }

    @Test
    void 지원하지_않는_형식과_10MB_초과는_거부() throws Exception {
        presign(me, "image/gif", 1000).andExpect(status().isBadRequest());
        presign(me, "image/jpeg", 10L * 1024 * 1024 + 1)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.details.contentLength").exists());
    }

    @Test
    void 업로드한_이미지를_순서대로_붙이고_조회_URL을_받는다() throws Exception {
        String k1 = uploaded(me);
        String k2 = uploaded(me);

        String id = idOf(write(me, List.of(k2, k1)).andExpect(status().isCreated())
            .andExpect(jsonPath("$.images[*].imageKey", contains(k2, k1)))
            .andExpect(jsonPath("$.images[0].order").value(0))
            .andExpect(jsonPath("$.images[0].url", startsWith("https://fake-storage/download/"))));

        mvc.perform(get("/api/v1/posts").header("Authorization", partner))
            .andExpect(jsonPath("$.content[0].id").value(id))
            .andExpect(jsonPath("$.content[0].images.length()").value(2));
    }

    @Test
    void 업로드_안_된_키_다른_커플_키_5장_중복은_거부() throws Exception {
        String notUploaded = presignKey(me);
        write(me, List.of(notUploaded)).andExpect(status().isBadRequest());

        String othersKey = uploaded(stranger);
        write(me, List.of(othersKey)).andExpect(status().isBadRequest());

        write(me, List.of(uploaded(me), uploaded(me), uploaded(me), uploaded(me), uploaded(me)))
            .andExpect(status().isBadRequest());

        String dup = uploaded(me);
        write(me, List.of(dup, dup)).andExpect(status().isBadRequest());
    }

    @Test
    void 수정시_빠진_이미지는_저장소에서도_삭제_null이면_유지() throws Exception {
        String keep = uploaded(me);
        String drop = uploaded(me);
        String add = uploaded(me);
        String id = idOf(write(me, List.of(keep, drop)));

        mvc.perform(patch("/api/v1/posts/" + id).header("Authorization", me)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("고침", List.of(add, keep))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.images[*].imageKey", contains(add, keep)));
        assertThat(storage.exists(drop)).isFalse();
        assertThat(storage.exists(keep)).isTrue();

        mvc.perform(patch("/api/v1/posts/" + id).header("Authorization", me)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("다시 고침", null)))
            .andExpect(jsonPath("$.images.length()").value(2));
    }

    @Test
    void 게시글_삭제시_이미지_파일도_삭제() throws Exception {
        String k = uploaded(me);
        String id = idOf(write(me, List.of(k)));

        mvc.perform(delete("/api/v1/posts/" + id).header("Authorization", me))
            .andExpect(status().isNoContent());

        assertThat(storage.exists(k)).isFalse();
    }

    @Test
    void 썸네일_크기를_보내면_썸네일_업로드_URL도_발급_안_보내면_null() throws Exception {
        mvc.perform(post("/api/v1/images/presigned-url").header("Authorization", me)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "contentType", "image/png", "contentLength", 2000, "thumbnailContentLength", 300))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.thumbnailUploadUrl", endsWith("_thumb.jpg")));

        presign(me, "image/jpeg", 1000).andExpect(jsonPath("$.thumbnailUploadUrl").doesNotExist());
    }

    @Test
    void 썸네일이_올라간_사진은_thumbnailUrl_없으면_원본URL_삭제시_썸네일도_삭제() throws Exception {
        String withThumb = uploaded(me);
        String thumb = ImageService.thumbnailKeyOf(withThumb);
        storage.upload(thumb, 300);
        String plain = uploaded(me);

        String id = idOf(write(me, List.of(withThumb, plain)).andExpect(status().isCreated())
            .andExpect(jsonPath("$.images[0].thumbnailUrl", endsWith(thumb)))
            .andExpect(jsonPath("$.images[0].url", endsWith(withThumb)))
            .andExpect(jsonPath("$.images[1].thumbnailUrl", endsWith(plain))));

        // 썸네일 키는 게시글 사진으로 직접 붙일 수 없음
        write(me, List.of(thumb)).andExpect(status().isBadRequest());

        mvc.perform(patch("/api/v1/posts/" + id).header("Authorization", me)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("고침", List.of(plain))))
            .andExpect(status().isOk());
        assertThat(storage.exists(withThumb)).isFalse();
        assertThat(storage.exists(thumb)).isFalse();
    }

    private ResultActions presign(String token, String contentType, long size) throws Exception {
        return mvc.perform(post("/api/v1/images/presigned-url").header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("contentType", contentType, "contentLength", size))));
    }

    private String presignKey(String token) throws Exception {
        JsonNode json = objectMapper.readTree(presign(token, "image/jpeg", 1000)
            .andReturn().getResponse().getContentAsString());
        return json.get("imageKey").asText();
    }

    /** presign 받고 업로드까지 끝난 상태의 키 */
    private String uploaded(String token) throws Exception {
        String key = presignKey(token);
        storage.upload(key, 1000);
        return key;
    }

    private ResultActions write(String token, List<String> imageKeys) throws Exception {
        return mvc.perform(post("/api/v1/posts").header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body("사진 일기", imageKeys)));
    }

    private String body(String content, List<String> imageKeys) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("content", content);
        body.put("postedDate", TODAY.toString());
        body.put("imageKeys", imageKeys);
        return objectMapper.writeValueAsString(body);
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
