package com.ourtoday.api.dev;

import com.ourtoday.api.auth.JwtTokenProvider;
import com.ourtoday.api.bucket.BucketItemService;
import com.ourtoday.api.bucket.dto.BucketItemCreateRequest;
import com.ourtoday.api.calendar.CalendarMemoService;
import com.ourtoday.api.calendar.dto.CalendarMemoCreateRequest;
import com.ourtoday.api.couple.CoupleService;
import com.ourtoday.api.post.PostService;
import com.ourtoday.api.post.dto.PostCreateRequest;
import com.ourtoday.api.reaction.EmojiType;
import com.ourtoday.api.reaction.ReactionService;
import com.ourtoday.api.user.UserService;
import com.ourtoday.api.user.dto.UserUpdateRequest;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * 로컬 화면 점검용 샘플 데이터 (테스트 코드라 운영 빌드에 안 들어감).
 * 실행: ops/local.sh (seed,dev 프로필) → 화면의 개발 로그인에 minji / jiho / solo
 * (커플 연결된 두 사람 + 커플 없는 한 사람). build/seed-tokens.txt 에 토큰도 기록
 */
@Component
@Profile("seed")
class LocalSeed implements CommandLineRunner {

    private final UserService userService;
    private final CoupleService coupleService;
    private final PostService postService;
    private final ReactionService reactionService;
    private final CalendarMemoService calendarMemoService;
    private final BucketItemService bucketItemService;
    private final JwtTokenProvider tokenProvider;

    LocalSeed(UserService userService, CoupleService coupleService, PostService postService,
              ReactionService reactionService, CalendarMemoService calendarMemoService,
              BucketItemService bucketItemService, JwtTokenProvider tokenProvider) {
        this.userService = userService;
        this.coupleService = coupleService;
        this.postService = postService;
        this.reactionService = reactionService;
        this.calendarMemoService = calendarMemoService;
        this.bucketItemService = bucketItemService;
        this.tokenProvider = tokenProvider;
    }

    @Override
    public void run(String... args) throws Exception {
        UUID a = user("minji", "민지");
        UUID b = user("jiho", "지호");
        coupleService.join(b, coupleService.createInvite(a).inviteCode());
        coupleService.updateStartedAt(a, today().minusDays(437));
        UUID solo = user("solo", "혼자");

        String[][] posts = {
            {"0", "a", "퇴근길에 같이 붕어빵 사 먹었다.\n팥이 꼬리까지 꽉 차 있어서 둘 다 감동\n내일도 저 가게 가자"},
            {"0", "b", "오늘은 각자 바빴지만 저녁 통화로 하루 정리.\n목소리 들으니까 피곤이 좀 풀렸다."},
            {"1", "a", "비 오는 날 우산 하나로 걸었다 ☔️\n어깨 반쪽씩 젖었지만 웃겼음"},
            {"2", "b", "새로 생긴 파스타집 성공!\n다음엔 크림 말고 오일로 먹어보기"},
            {"3", "a", "영화 보다가 둘 다 졸았다 😴\n그래도 팝콘은 다 먹음"},
            {"365", "a", "작년 오늘, 처음으로 같이 한강 피크닉 🌿\n돗자리 깔자마자 바람이 엄청 불었지"},
            {"730", "b", "재작년 오늘의 기록. 첫 여행 계획 세우기!"},
        };
        UUID firstPost = null;
        for (String[] p : posts) {
            UUID author = p[1].equals("a") ? a : b;
            var res = postService.create(author, new PostCreateRequest(p[2], today().minusDays(Long.parseLong(p[0])), List.of()));
            if (firstPost == null) firstPost = res.id();
        }
        reactionService.toggle(b, firstPost, EmojiType.HEART);
        reactionService.toggle(b, firstPost, EmojiType.LAUGH);
        reactionService.toggle(a, firstPost, EmojiType.HEART);

        calendarMemoService.create(a, new CalendarMemoCreateRequest(today().plusDays(5), "1주년 기념일 🎂", "레스토랑 7시 예약", "#FF7A8A"));
        calendarMemoService.create(b, new CalendarMemoCreateRequest(today().plusDays(12), "부산 여행", null, "#6FB3E8"));
        calendarMemoService.create(a, new CalendarMemoCreateRequest(today(), "치과 예약", null, "#7CC69A"));

        bucketItemService.create(a, new BucketItemCreateRequest("오로라 보러 가기", "아이슬란드 겨울", LocalDate.of(2027, 1, 15)));
        bucketItemService.create(b, new BucketItemCreateRequest("같이 마라톤 10km 완주", null, null));
        var done = bucketItemService.create(a, new BucketItemCreateRequest("한강 피크닉", null, null));
        bucketItemService.complete(a, done.id(), null);

        Files.writeString(Path.of("build/seed-tokens.txt"), String.join("\n",
            "a=" + tokenProvider.generateAccessToken(a),
            "aRefresh=" + tokenProvider.generateRefreshToken(a),
            "b=" + tokenProvider.generateAccessToken(b),
            "solo=" + tokenProvider.generateAccessToken(solo)) + "\n");
    }

    /** 임시 로그인(dev 프로필)에서 같은 이름으로 들어올 수 있게 kakaoId 를 dev:{name} 으로 */
    private UUID user(String name, String nickname) {
        UUID id = userService.findOrCreateByKakaoId("dev:" + name).getId();
        userService.updateMe(id, new UserUpdateRequest(nickname, null));
        return id;
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Seoul"));
    }
}
