package com.ourtoday.api.post;

import com.ourtoday.api.common.error.BusinessException;
import com.ourtoday.api.common.error.ErrorCode;
import com.ourtoday.api.couple.CoupleService;
import com.ourtoday.api.image.ImageService;
import com.ourtoday.api.post.dto.PostCreateRequest;
import com.ourtoday.api.post.dto.PostResponse;
import com.ourtoday.api.post.dto.PostUpdateRequest;
import com.ourtoday.api.user.User;
import com.ourtoday.api.user.UserRepository;
import lombok.RequiredArgsConstructor;
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
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostService {

    /** 목록 정렬 허용 필드 (그 외는 VALIDATION_FAILED) */
    static final Set<String> SORTABLE = Set.of("postedDate", "createdAt");
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final PostRepository postRepository;
    private final PostImageRepository postImageRepository;
    private final UserRepository userRepository;
    private final CoupleService coupleService;
    private final ImageService imageService;
    private final Clock clock;

    @Transactional
    public PostResponse create(UUID userId, PostCreateRequest request) {
        validatePostedDate(request.postedDate());
        UUID coupleId = coupleService.getConnectedCoupleId(userId);
        List<String> imageKeys = normalizeImageKeys(request.imageKeys());
        imageService.verifyAttachable(coupleId, imageKeys);

        Post post = postRepository.save(Post.create(coupleId, userId, request.content().strip(), request.postedDate()));
        saveImages(post.getId(), imageKeys);
        return toResponse(post);
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

        Map<UUID, User> users = loadUsers(page.map(Post::getAuthorId).toSet());
        Map<UUID, List<PostImage>> images = loadImages(page.map(Post::getId).toSet());
        return page.map(p -> toResponse(p, users, images));
    }

    @Transactional(readOnly = true)
    public PostResponse get(UUID userId, UUID postId) {
        return toResponse(getPostInMyCouple(userId, postId));
    }

    @Transactional
    public PostResponse update(UUID userId, UUID postId, PostUpdateRequest request) {
        validatePostedDate(request.postedDate());
        Post post = getMyPost(userId, postId);
        post.update(request.content().strip(), request.postedDate());

        if (request.imageKeys() != null) {
            List<String> newKeys = normalizeImageKeys(request.imageKeys());
            List<String> oldKeys = postImageRepository.findByPostIdOrderByDisplayOrderAsc(postId).stream()
                .map(PostImage::getImageKey)
                .toList();
            // 기존 이미지는 이미 검증됨 → 새로 추가된 것만 검증
            imageService.verifyAttachable(post.getCoupleId(), newKeys.stream().filter(k -> !oldKeys.contains(k)).toList());

            postImageRepository.deleteByPostId(postId);
            postImageRepository.flush();
            saveImages(postId, newKeys);
            imageService.deleteAfterCommit(oldKeys.stream().filter(k -> !newKeys.contains(k)).toList());
        }
        return toResponse(post);
    }

    @Transactional
    public void delete(UUID userId, UUID postId) {
        Post post = getMyPost(userId, postId);
        List<String> keys = postImageRepository.findByPostIdOrderByDisplayOrderAsc(postId).stream()
            .map(PostImage::getImageKey)
            .toList();
        postImageRepository.deleteByPostId(postId);
        postRepository.delete(post);
        imageService.deleteAfterCommit(keys);
    }

    private Post getPostInMyCouple(UUID userId, UUID postId) {
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
            postImageRepository.save(PostImage.of(postId, keys.get(i), i));
        }
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

    private PostResponse toResponse(Post post) {
        return toResponse(post, loadUsers(Set.of(post.getAuthorId())), loadImages(Set.of(post.getId())));
    }

    private PostResponse toResponse(Post post, Map<UUID, User> users, Map<UUID, List<PostImage>> images) {
        User author = users.get(post.getAuthorId());
        List<PostResponse.Image> imageResponses = images.getOrDefault(post.getId(), List.of()).stream()
            .map(img -> new PostResponse.Image(
                imageService.downloadUrl(img.getImageKey()), img.getImageKey(), img.getDisplayOrder()))
            .toList();
        return new PostResponse(
            post.getId(),
            new PostResponse.Author(author.getId(), author.getNickname(), author.getProfileImageUrl()),
            post.getContent(),
            post.getPostedDate(),
            imageResponses,
            post.getCreatedAt(),
            post.getUpdatedAt()
        );
    }
}
