package com.ourtoday.api.account;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ourtoday.api.auth.JwtTokenProvider;
import com.ourtoday.api.couple.CoupleService;
import com.ourtoday.api.image.FakeImageStorage;
import com.ourtoday.api.image.TestStorageConfig;
import com.ourtoday.api.post.PostRepository;
import com.ourtoday.api.user.UserRepository;
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
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestStorageConfig.class)
@Transactional
class MyPageFlowTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserService userService;
    @Autowired CoupleService coupleService;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired FakeImageStorage storage;
    @Autowired UserRepository userRepository;
    @Autowired PostRepository postRepository;

    UUID meId;
    UUID partnerId;
    String me;
    String partner;

    @BeforeEach
    void setUp() {
        meId = newUser("민지");
        partnerId = newUser("지호");
        coupleService.join(partnerId, coupleService.createInvite(meId).inviteCode());
        me = token(meId);
        partner = token(partnerId);
    }

    @Test
    void 알림_설정_조회와_수정() throws Exception {
        mvc.perform(get("/api/v1/users/me/settings").header("Authorization", me))
            .andExpect(jsonPath("$.notifyMemoryToday").value(true))
            .andExpect(jsonPath("$.notifyPartnerPost").value(true));

        mvc.perform(patch("/api/v1/users/me/settings").header("Authorization", me)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"notifyMemoryToday\":false,\"notifyPartnerPost\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notifyMemoryToday").value(false));

        mvc.perform(get("/api/v1/users/me/settings").header("Authorization", me))
            .andExpect(jsonPath("$.notifyMemoryToday").value(false));

        mvc.perform(patch("/api/v1/users/me/settings").header("Authorization", me)
                .contentType(MediaType.APPLICATION_JSON).content("{\"notifyMemoryToday\":false}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 사귄_날짜_수정은_통계에도_반영되고_미래는_불가() throws Exception {
        mvc.perform(patch("/api/v1/couples/me").header("Authorization", partner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"startedAt\":\"" + TODAY.minusDays(99) + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.startedAt").value(TODAY.minusDays(99).toString()));

        mvc.perform(get("/api/v1/couples/me/stats").header("Authorization", me))
            .andExpect(jsonPath("$.couple.daysTogether").value(100));

        mvc.perform(patch("/api/v1/couples/me").header("Authorization", me)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"startedAt\":\"" + TODAY.plusDays(1) + "\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 커플_해제하면_둘_다_커플_없음이고_다시_연결할_수_있다() throws Exception {
        String postId = writePost(me, "함께 쓴 글", List.of());

        mvc.perform(delete("/api/v1/couples/me").header("Authorization", partner))
            .andExpect(status().isNoContent());

        for (String token : List.of(me, partner)) {
            mvc.perform(get("/api/v1/users/me/onboarding-status").header("Authorization", token))
                .andExpect(jsonPath("$.status").value("NO_COUPLE"));
            mvc.perform(get("/api/v1/posts/" + postId).header("Authorization", token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("COUPLE_REQUIRED"));
        }

        coupleService.join(partnerId, coupleService.createInvite(meId).inviteCode());
        mvc.perform(get("/api/v1/posts").header("Authorization", me))
            .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void 내보내기_zip_에_글과_사진이_들어간다() throws Exception {
        String key = upload(me, 5);
        writePost(me, "사진 있는 글", List.of(key));
        String partnerPostId = writePost(partner, "상대 글", List.of());
        mvc.perform(post("/api/v1/posts/" + partnerPostId + "/comments").header("Authorization", me)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"내보낼 댓글\"}"))
            .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/calendar/memos").header("Authorization", me)
            .contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"2026-12-25\",\"title\":\"크리스마스\"}"));

        JsonNode res = json(mvc.perform(post("/api/v1/users/me/export").header("Authorization", partner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.expiresAt").exists()));
        String url = res.get("downloadUrl").asText();
        String zipKey = url.substring("https://fake-storage/download/".length(), url.indexOf('?'));
        assertThat(zipKey).startsWith("exports/" + partnerId + "/");

        Map<String, byte[]> entries = unzip(storage.content(zipKey));
        assertThat(entries).containsKey("data.json");
        JsonNode data = objectMapper.readTree(entries.get("data.json"));
        assertThat(data.get("couple").get("members").toString()).contains("민지", "지호");
        assertThat(data.get("posts")).hasSize(2);
        JsonNode exported = data.get("posts").findValues("comments").stream()
            .filter(n -> !n.isEmpty()).findFirst().orElseThrow().get(0);
        assertThat(exported.get("content").asText()).isEqualTo("내보낼 댓글");
        assertThat(exported.get("author").asText()).isEqualTo("민지");
        assertThat(data.get("calendarMemos").get(0).get("title").asText()).isEqualTo("크리스마스");
        String imageEntry = data.get("posts").findValues("images").stream()
            .filter(n -> !n.isEmpty()).findFirst().orElseThrow().get(0).asText();
        assertThat(entries.get(imageEntry)).hasSize(5);

        mvc.perform(post("/api/v1/users/me/export").header("Authorization", partner))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 탈퇴하면_내_기록과_계정이_지워지고_상대는_커플_없음() throws Exception {
        writePost(me, "내 글", List.of());
        writePost(partner, "상대 글", List.of());

        mvc.perform(delete("/api/v1/users/me").header("Authorization", me))
            .andExpect(status().isNoContent());

        assertThat(userRepository.findById(meId)).isEmpty();
        assertThat(postRepository.findByAuthorId(meId)).isEmpty();
        assertThat(postRepository.findByAuthorId(partnerId)).hasSize(1);
        mvc.perform(get("/api/v1/users/me/onboarding-status").header("Authorization", partner))
            .andExpect(jsonPath("$.status").value("NO_COUPLE"));
        mvc.perform(get("/api/v1/posts").header("Authorization", me))
            .andExpect(status().isUnauthorized());
    }

    private String upload(String token, int size) throws Exception {
        JsonNode res = json(mvc.perform(post("/api/v1/images/presigned-url").header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"contentType\":\"image/jpeg\",\"contentLength\":" + size + "}")));
        String key = res.get("imageKey").asText();
        storage.upload(key, size);
        return key;
    }

    private String writePost(String token, String content, List<String> imageKeys) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("content", content);
        body.put("postedDate", TODAY.toString());
        body.put("imageKeys", imageKeys);
        return json(mvc.perform(post("/api/v1/posts").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated())).get("id").asText();
    }

    private JsonNode json(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static Map<String, byte[]> unzip(byte[] zip) throws Exception {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
                entries.put(e.getName(), in.readAllBytes());
            }
        }
        return entries;
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
