package com.ourtoday.api.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ourtoday.api.account.dto.ExportResponse;
import com.ourtoday.api.bucket.BucketItem;
import com.ourtoday.api.bucket.BucketItemRepository;
import com.ourtoday.api.calendar.CalendarMemo;
import com.ourtoday.api.calendar.CalendarMemoRepository;
import com.ourtoday.api.common.error.BusinessException;
import com.ourtoday.api.common.error.ErrorCode;
import com.ourtoday.api.couple.Couple;
import com.ourtoday.api.couple.CoupleMember;
import com.ourtoday.api.couple.CoupleMemberRepository;
import com.ourtoday.api.couple.CoupleRepository;
import com.ourtoday.api.image.ImageStorage;
import com.ourtoday.api.image.StorageProperties;
import com.ourtoday.api.post.Post;
import com.ourtoday.api.post.PostImage;
import com.ourtoday.api.post.PostImageRepository;
import com.ourtoday.api.post.PostRepository;
import com.ourtoday.api.reaction.ReactionCount;
import com.ourtoday.api.reaction.ReactionRepository;
import com.ourtoday.api.user.User;
import com.ourtoday.api.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 데이터 내보내기: data.json + images/ 를 zip 으로 묶어 저장소에 올리고 임시 다운로드 URL 반환 (동기).
 * DB 는 짧은 읽기 트랜잭션에서만 쓰고, 사진 다운로드·zip·업로드는 트랜잭션 밖에서 (커넥션 점유 방지)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportService {

    private final UserRepository userRepository;
    private final CoupleRepository coupleRepository;
    private final CoupleMemberRepository coupleMemberRepository;
    private final PostRepository postRepository;
    private final PostImageRepository postImageRepository;
    private final ReactionRepository reactionRepository;
    private final CalendarMemoRepository calendarMemoRepository;
    private final BucketItemRepository bucketItemRepository;
    private final ImageStorage storage;
    private final StorageProperties storageProperties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final PlatformTransactionManager transactionManager;

    /** 사용자별 진행 중/최근 내보내기 시각 (서버 1대라 메모리로 충분) */
    private final Map<UUID, Instant> recent = new ConcurrentHashMap<>();
    static final Duration COOLDOWN = Duration.ofMinutes(1);

    public ExportResponse export(UUID userId) {
        Instant now = clock.instant();
        Instant previous = recent.putIfAbsent(userId, now);
        if (previous != null) {
            if (previous.plus(COOLDOWN).isAfter(now)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "내보내기는 1분에 한 번만 할 수 있어요. 잠시 후 다시 시도해 주세요.");
            }
            recent.put(userId, now);
        }
        try {
            Snapshot snapshot = readOnly().execute(status -> snapshot(userId));
            return writeAndUpload(userId, snapshot);
        } catch (RuntimeException e) {
            recent.remove(userId);
            throw e;
        }
    }

    private TransactionTemplate readOnly() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setReadOnly(true);
        return template;
    }

    private record Snapshot(Map<String, Object> data, List<String> imageEntries, List<String> imageKeys) {}

    private Snapshot snapshot(UUID userId) {
        User me = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        Optional<Couple> couple = coupleRepository.findByMemberUserId(userId).filter(Couple::isConnected);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("exportedAt", clock.instant());
        data.put("user", Map.of("nickname", me.getNickname(), "joinedAt", me.getCreatedAt()));
        List<String> imageEntries = new ArrayList<>();
        List<String> imageKeys = new ArrayList<>();
        couple.ifPresent(c -> fillCoupleData(c, data, imageEntries, imageKeys));
        if (couple.isEmpty()) {
            data.put("couple", null);
        }
        return new Snapshot(data, imageEntries, imageKeys);
    }

    private ExportResponse writeAndUpload(UUID userId, Snapshot snapshot) {
        Path zip = null;
        try {
            zip = Files.createTempFile("ourtoday-export-", ".zip");
            writeZip(zip, snapshot.data(), snapshot.imageEntries(), snapshot.imageKeys());
            String key = "exports/" + userId + "/" + UUID.randomUUID() + ".zip";
            storage.put(key, zip, "application/zip");
            String fileName = "ourtoday-" + LocalDate.now(clock) + ".zip";
            return new ExportResponse(storage.presignAttachment(key, fileName),
                Instant.now(clock).plus(storageProperties.downloadUrlTtl()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            deleteQuietly(zip);
        }
    }

    private void fillCoupleData(Couple couple, Map<String, Object> data, List<String> imageEntries,
                                List<String> imageKeys) {
        List<UUID> memberIds = coupleMemberRepository.findByCoupleIdOrderByJoinedAtAsc(couple.getId()).stream()
            .map(CoupleMember::getUserId).toList();
        Map<UUID, String> nicknames = userRepository.findAllById(memberIds).stream()
            .collect(Collectors.toMap(User::getId, User::getNickname));
        Function<UUID, String> name = id -> nicknames.getOrDefault(id, "(알 수 없음)");

        data.put("couple", Map.of(
            "startedAt", couple.getStartedAt(),
            "members", memberIds.stream().map(name).toList()));

        List<Post> posts = postRepository.findByCoupleIdOrderByPostedDateAscCreatedAtAsc(couple.getId());
        List<UUID> postIds = posts.stream().map(Post::getId).toList();
        Map<UUID, List<PostImage>> images = postIds.isEmpty() ? Map.of()
            : postImageRepository.findByPostIdInOrderByDisplayOrderAsc(postIds).stream()
                .collect(Collectors.groupingBy(PostImage::getPostId));
        Map<UUID, List<ReactionCount>> reactions = postIds.isEmpty() ? Map.of()
            : reactionRepository.countByPostIds(postIds, UUID.randomUUID()).stream()
                .collect(Collectors.groupingBy(ReactionCount::postId));

        List<Map<String, Object>> postData = new ArrayList<>();
        for (Post post : posts) {
            List<String> files = new ArrayList<>();
            for (PostImage image : images.getOrDefault(post.getId(), List.of())) {
                String key = image.getImageKey();
                String entry = "images/" + post.getPostedDate() + "_" + post.getId().toString().substring(0, 8)
                    + "_" + (image.getDisplayOrder() + 1) + key.substring(key.lastIndexOf('.'));
                files.add(entry);
                imageEntries.add(entry);
                imageKeys.add(key);
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("postedDate", post.getPostedDate());
            row.put("author", name.apply(post.getAuthorId()));
            row.put("content", post.getContent());
            row.put("images", files);
            row.put("reactions", reactions.getOrDefault(post.getId(), List.of()).stream()
                .collect(Collectors.toMap(r -> r.emojiType().name(), ReactionCount::count,
                    (a, b) -> a, LinkedHashMap::new)));
            row.put("createdAt", post.getCreatedAt());
            postData.add(row);
        }
        data.put("posts", postData);

        data.put("calendarMemos", calendarMemoRepository.findByCoupleIdOrderByDateAscCreatedAtAsc(couple.getId())
            .stream().map(m -> memoRow(m, name)).toList());
        data.put("bucketItems", bucketItemRepository.findByCoupleIdOrderByCompletedAscCreatedAtDesc(couple.getId())
            .stream().map(b -> bucketRow(b, name)).toList());
    }

    private Map<String, Object> memoRow(CalendarMemo memo, Function<UUID, String> name) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("date", memo.getDate());
        row.put("title", memo.getTitle());
        row.put("description", memo.getDescription());
        row.put("author", name.apply(memo.getAuthorId()));
        return row;
    }

    private Map<String, Object> bucketRow(BucketItem item, Function<UUID, String> name) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("title", item.getTitle());
        row.put("description", item.getDescription());
        row.put("targetDate", item.getTargetDate());
        row.put("completed", item.isCompleted());
        row.put("completedAt", item.getCompletedAt());
        row.put("createdBy", name.apply(item.getCreatedBy()));
        return row;
    }

    private void writeZip(Path zip, Map<String, Object> data, List<String> entries, List<String> keys)
        throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("data.json"));
            out.write(objectMapper.copy().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .writerWithDefaultPrettyPrinter().writeValueAsBytes(data));
            out.closeEntry();
            List<String> missing = new ArrayList<>();
            Path buffer = Files.createTempFile("ourtoday-img-", ".bin");
            try {
                for (int i = 0; i < entries.size(); i++) {
                    // 먼저 임시 파일로 받아서, 중간에 실패한 사진이 깨진 채 zip 에 들어가지 않게
                    try (OutputStream tmp = Files.newOutputStream(buffer)) {
                        storage.copyTo(keys.get(i), tmp);
                    } catch (RuntimeException e) {
                        log.warn("내보내기 이미지 누락 key={}", keys.get(i), e);
                        missing.add(entries.get(i));
                        continue;
                    }
                    out.putNextEntry(new ZipEntry(entries.get(i)));
                    Files.copy(buffer, out);
                    out.closeEntry();
                }
            } finally {
                deleteQuietly(buffer);
            }
            if (!missing.isEmpty()) {
                out.putNextEntry(new ZipEntry("missing-images.txt"));
                out.write(("받지 못한 사진:\n" + String.join("\n", missing) + "\n").getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 임시 파일이라 무시
        }
    }
}
