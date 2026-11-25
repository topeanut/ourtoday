package com.ourtoday.api.post;

import com.ourtoday.api.bucket.BucketItemRepository;
import com.ourtoday.api.common.error.BusinessException;
import com.ourtoday.api.common.error.ErrorCode;
import com.ourtoday.api.comment.Comment;
import com.ourtoday.api.comment.CommentRepository;
import com.ourtoday.api.comment.dto.CommentResponse;
import com.ourtoday.api.couple.CoupleService;
import com.ourtoday.api.image.ImageService;
import com.ourtoday.api.post.dto.PostCreateRequest;
import com.ourtoday.api.post.dto.PostMemoriesResponse;
import com.ourtoday.api.post.dto.PlaceLocation;
import com.ourtoday.api.post.dto.PostResponse;
import com.ourtoday.api.post.dto.PostUpdateRequest;
import com.ourtoday.api.reaction.ReactionCount;
import com.ourtoday.api.reaction.ReactionRepository;
import com.ourtoday.api.user.ProfileImageResolver;
import com.ourtoday.api.user.User;
import com.ourtoday.api.user.UserRepository;
import lombok.RequiredArgsConstructor;
import com.ourtoday.api.push.event.PostCreatedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class PostService {

    /** 목록 정렬 허용 필드 (그 외는 VALIDATION_FAILED) */
    static final Set<String> SORTABLE = Set.of("postedDate", "createdAt");
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final PostRepository postRepository;
    private final PostImageRepository postImageRepository;
    private final ReactionRepository reactionRepository;
    private final CommentRepository commentRepository;
    private final BucketItemRepository bucketItemRepository;
    private final UserRepository userRepository;
    private final CoupleService coupleService;
    private final ImageService imageService;
    private final Clock clock;
    private final ApplicationEventPublisher publisher;
    private final ProfileImageResolver profileImages;

    @Transactional
    public PostResponse create(UUID userId, PostCreateRequest request) {
        validatePostedDate(request.postedDate());
        UUID coupleId = coupleService.getConnectedCoupleId(userId);
        List<String> imageKeys = normalizeImageKeys(request.imageKeys());
        imageService.verifyAttachable(coupleId, imageKeys);

        Post post = postRepository.saveAndFlush(Post.create(coupleId, userId, request.content().strip(), request.postedDate(),
            lat(request.location()), lng(request.location()), name(request.location())));
        saveImages(post.getId(), imageKeys);
        publisher.publishEvent(new PostCreatedEvent(post.getId(), userId, coupleId, post.getPostedDate()));
        return toResponse(post, userId);
    }

    /**
     * @param author "me" / "partner" / null(전체)
     */
    @Transactional(readOnly = true)
    public Page<PostResponse> list(UUID userId, String author, LocalDate from, LocalDate to, Pageable pageable) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "from 은 to 보다 늦을 수 없습니다.");
        }
        UUID coupleId = coupleService.getConnectedCoupleId(userId);
        UUID authorId = resolveAuthor(author, coupleId, userId);

        Specification<Post> spec = Specification.allOf(
            PostSpecs.inCouple(coupleId),
            PostSpecs.writtenBy(authorId),
            PostSpecs.postedFrom(from),
            PostSpecs.postedTo(to)
        );
        Page<Post> page = postRepository.findAll(spec, withStableSort(pageable));

        Set<UUID> postIds = page.map(Post::getId).toSet();
        Map<UUID, List<Comment>> comments = loadComments(postIds);
        Map<UUID, User> users = loadUsers(authorIds(page.getContent(), comments));
        Map<UUID, List<PostImage>> images = loadImages(postIds);
        Map<UUID, List<ReactionCount>> reactions = loadReactions(postIds, userId);
        return page.map(p -> toResponse(p, users, images, reactions, comments));
    }

    @Transactional(readOnly = true)
    public PostResponse get(UUID userId, UUID postId) {
        return toResponse(getPostInMyCouple(userId, postId), userId);
    }

    @Transactional
    public PostResponse update(UUID userId, UUID postId, PostUpdateRequest request) {
        validatePostedDate(request.postedDate());
        Post post = getMyPost(userId, postId);
        post.update(request.content().strip(), request.postedDate());
        post.updateLocation(lat(request.location()), lng(request.location()), name(request.location()));

        if (request.imageKeys() != null) {
            List<String> newKeys = normalizeImageKeys(request.imageKeys());
            List<PostImage> oldImages = postImageRepository.findByPostIdOrderByDisplayOrderAsc(postId);
            List<String> oldKeys = oldImages.stream().map(PostImage::getImageKey).toList();
            // 기존 이미지는 이미 검증됨 → 새로 추가된 것만 검증
            imageService.verifyAttachable(post.getCoupleId(), newKeys.stream().filter(k -> !oldKeys.contains(k)).toList());

            postImageRepository.deleteByPostId(postId);
            postImageRepository.flush();
            saveImages(postId, newKeys);
            imageService.deleteAfterCommit(fileKeys(oldImages.stream().filter(i -> !newKeys.contains(i.getImageKey())).toList()));
        }
        // 응답의 updatedAt 이 반영되도록 (@UpdateTimestamp 는 flush 때 채워짐)
        postRepository.flush();
        return toResponse(post, userId);
    }

    @Transactional
    public void delete(UUID userId, UUID postId) {
        deleteWithRelations(getMyPost(userId, postId));
    }

    /** 탈퇴용: 이 사용자가 쓴 글 전부 (이미지·반응 포함) */
    @Transactional
    public void deleteAllWrittenBy(UUID userId) {
        postRepository.findByAuthorId(userId).forEach(this::deleteWithRelations);
    }

    private void deleteWithRelations(Post post) {
        UUID postId = post.getId();
        List<String> keys = fileKeys(postImageRepository.findByPostIdOrderByDisplayOrderAsc(postId));
        postImageRepository.deleteByPostId(postId);
        reactionRepository.deleteByPostId(postId);
        commentRepository.deleteByPostId(postId);
        postRepository.delete(post);
        bucketItemRepository.unlinkPost(postId);
        imageService.deleteAfterCommit(keys);
    }

    /** 작년 오늘: 오늘(Asia/Seoul)과 같은 월/일의 지난 연도 글을 연도별로 */
    @Transactional(readOnly = true)
    public PostMemoriesResponse memoriesToday(UUID userId) {
        UUID coupleId = coupleService.getConnectedCoupleId(userId);
        LocalDate today = LocalDate.now(clock.withZone(SERVICE_ZONE));
        List<Post> posts = postRepository.findMemories(coupleId, today.getMonthValue(), today.getDayOfMonth(),
            today.withDayOfYear(1));

        Set<UUID> postIds = posts.stream().map(Post::getId).collect(Collectors.toSet());
        Map<UUID, List<Comment>> comments = loadComments(postIds);
        Map<UUID, User> users = loadUsers(authorIds(posts, comments));
        Map<UUID, List<PostImage>> images = loadImages(postIds);
        Map<UUID, List<ReactionCount>> reactions = loadReactions(postIds, userId);

        Map<Integer, List<PostResponse>> byYear = new LinkedHashMap<>();
        for (Post p : posts) {
            byYear.computeIfAbsent(p.getPostedDate().getYear(), y -> new ArrayList<>())
                .add(toResponse(p, users, images, reactions, comments));
        }
        return new PostMemoriesResponse(today, byYear.entrySet().stream()
            .map(e -> new PostMemoriesResponse.YearMemories(e.getKey(), e.getValue()))
            .toList());
    }

    /** 우리 커플 글인지 확인 후 반환 (반응 등 다른 도메인에서도 사용) */
    @Transactional(readOnly = true)
    public Post getPostInMyCouple(UUID userId, UUID postId) {
        UUID coupleId = coupleService.getConnectedCoupleId(userId);
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "게시글을 찾을 수 없습니다."));
        if (!post.getCoupleId().equals(coupleId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "우리 커플의 게시글이 아닙니다.");
        }
        return post;
    }

    private Post getMyPost(UUID userId, UUID postId) {
        Post post = getPostInMyCouple(userId, postId);
        if (!post.isWrittenBy(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "작성자만 수정/삭제할 수 있습니다.");
        }
        return post;
    }

    private UUID resolveAuthor(String author, UUID coupleId, UUID userId) {
        if (author == null || author.isBlank()) {
            return null;
        }
        return switch (author.strip().toLowerCase()) {
            case "me" -> userId;
            case "partner" -> coupleService.getPartnerId(coupleId, userId);
            default -> throw new BusinessException(ErrorCode.VALIDATION_FAILED, "author 는 me 또는 partner 입니다.");
        };
    }

    /** 허용 필드만 받고, 같은 값끼리 순서가 흔들리지 않게 createdAt·id 보조 정렬 추가 */
    private Pageable withStableSort(Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE.contains(order.getProperty())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "sort 는 " + SORTABLE + " 중 하나입니다: " + order.getProperty());
            }
        }
        Sort sort = pageable.getSort().isSorted()
            ? pageable.getSort()
            : Sort.by(Sort.Direction.DESC, "postedDate");
        if (sort.getOrderFor("createdAt") == null) {
            sort = sort.and(Sort.by(Sort.Direction.DESC, "createdAt"));
        }
        sort = sort.and(Sort.by(Sort.Direction.DESC, "id"));
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    private void validatePostedDate(LocalDate postedDate) {
        if (postedDate.isAfter(LocalDate.now(clock.withZone(SERVICE_ZONE)))) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "postedDate 는 오늘 이후일 수 없습니다.");
        }
    }

    private List<String> normalizeImageKeys(List<String> keys) {
        if (keys == null) {
            return List.of();
        }
        List<String> stripped = keys.stream().map(String::strip).toList();
        if (new HashSet<>(stripped).size() != stripped.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "같은 이미지를 두 번 넣을 수 없습니다.");
        }
        return stripped;
    }

    private void saveImages(UUID postId, List<String> keys) {
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            postImageRepository.save(PostImage.of(postId, key, imageService.uploadedThumbnailKey(key), i));
        }
    }

    /** 저장소에서 지울 파일: 원본 + 있으면 썸네일 */
    private static List<String> fileKeys(List<PostImage> images) {
        return images.stream()
            .flatMap(i -> i.getThumbnailKey() == null ? Stream.of(i.getImageKey()) : Stream.of(i.getImageKey(), i.getThumbnailKey()))
            .toList();
    }

    private Map<UUID, User> loadUsers(Collection<UUID> ids) {
        return userRepository.findAllById(ids).stream()
            .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private Map<UUID, List<PostImage>> loadImages(Collection<UUID> postIds) {
        if (postIds.isEmpty()) {
            return Map.of();
        }
        return postImageRepository.findByPostIdInOrderByDisplayOrderAsc(postIds).stream()
            .collect(Collectors.groupingBy(PostImage::getPostId));
    }

    private Map<UUID, List<ReactionCount>> loadReactions(Collection<UUID> postIds, UUID viewerId) {
        if (postIds.isEmpty()) {
            return Map.of();
        }
        return reactionRepository.countByPostIds(postIds, viewerId).stream()
            .collect(Collectors.groupingBy(ReactionCount::postId));
    }

    private Map<UUID, List<Comment>> loadComments(Collection<UUID> postIds) {
        if (postIds.isEmpty()) {
            return Map.of();
        }
        return commentRepository.findByPostIdInOrderByCreatedAtAsc(postIds).stream()
            .collect(Collectors.groupingBy(Comment::getPostId));
    }

    /** 글 작성자 + 미리보기에 나올 댓글 작성자 (한 번에 조회) */
    private Set<UUID> authorIds(Collection<Post> posts, Map<UUID, List<Comment>> comments) {
        Set<UUID> ids = new HashSet<>();
        for (Post p : posts) {
            ids.add(p.getAuthorId());
            recent(comments.getOrDefault(p.getId(), List.of())).forEach(c -> ids.add(c.getAuthorId()));
        }
        return ids;
    }

    /** 최신 2개, 오래된 순 유지 */
    private List<Comment> recent(List<Comment> comments) {
        return comments.subList(Math.max(0, comments.size() - 2), comments.size());
    }

    private static Double lat(PlaceLocation l) { return l == null ? null : l.lat(); }

    private static Double lng(PlaceLocation l) { return l == null ? null : l.lng(); }

    private static String name(PlaceLocation l) { return l == null ? null : l.placeName().strip(); }

    private static PlaceLocation toLocation(Post post) {
        if (post.getPlaceLat() == null || post.getPlaceLng() == null || post.getPlaceName() == null) {
            return null;
        }
        return new PlaceLocation(post.getPlaceLat(), post.getPlaceLng(), post.getPlaceName());
    }

    private PostResponse toResponse(Post post, UUID viewerId) {
        Set<UUID> ids = Set.of(post.getId());
        Map<UUID, List<Comment>> comments = loadComments(ids);
        return toResponse(post, loadUsers(authorIds(List.of(post), comments)), loadImages(ids),
            loadReactions(ids, viewerId), comments);
    }

    private PostResponse toResponse(Post post, Map<UUID, User> users, Map<UUID, List<PostImage>> images,
                                    Map<UUID, List<ReactionCount>> reactions, Map<UUID, List<Comment>> comments) {
        User author = users.get(post.getAuthorId());
        List<PostResponse.Image> imageResponses = images.getOrDefault(post.getId(), List.of()).stream()
            .map(img -> {
                String url = imageService.downloadUrl(img.getImageKey());
                String thumbnailUrl = img.getThumbnailKey() == null ? url : imageService.downloadUrl(img.getThumbnailKey());
                return new PostResponse.Image(url, thumbnailUrl, img.getImageKey(), img.getDisplayOrder());
            })
            .toList();
        List<PostResponse.Reaction> reactionResponses = reactions.getOrDefault(post.getId(), List.of()).stream()
            .sorted(Comparator.comparing(ReactionCount::emojiType))
            .map(r -> new PostResponse.Reaction(r.emojiType(), r.count(), r.reactedByMe()))
            .toList();
        List<Comment> postComments = comments.getOrDefault(post.getId(), List.of());
        List<CommentResponse> recentComments = recent(postComments).stream()
            .map(c -> commentResponse(c, users.get(c.getAuthorId())))
            .toList();
        return new PostResponse(
            post.getId(),
            new PostResponse.Author(author.getId(), author.getNickname(), profileImages.urlOf(author)),
            post.getContent(),
            post.getPostedDate(),
            imageResponses,
            toLocation(post),
            reactionResponses,
            postComments.size(),
            recentComments,
            post.getCreatedAt(),
            post.getUpdatedAt()
        );
    }

    private CommentResponse commentResponse(Comment comment, User author) {
        return CommentResponse.of(comment, author, profileImages.urlOf(author));
    }
}
