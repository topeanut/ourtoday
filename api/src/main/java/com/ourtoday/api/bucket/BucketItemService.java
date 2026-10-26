package com.ourtoday.api.bucket;

import com.ourtoday.api.bucket.dto.BucketItemCreateRequest;
import com.ourtoday.api.bucket.dto.BucketItemListResponse;
import com.ourtoday.api.bucket.dto.BucketItemResponse;
import com.ourtoday.api.bucket.dto.BucketItemUpdateRequest;
import com.ourtoday.api.common.error.BusinessException;
import com.ourtoday.api.common.error.ErrorCode;
import com.ourtoday.api.couple.CoupleService;
import com.ourtoday.api.post.PostService;
import com.ourtoday.api.user.User;
import com.ourtoday.api.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BucketItemService {

    private final BucketItemRepository bucketItemRepository;
    private final UserRepository userRepository;
    private final CoupleService coupleService;
    private final PostService postService;
    private final Clock clock;

    @Transactional
    public BucketItemResponse create(UUID userId, BucketItemCreateRequest request) {
        UUID coupleId = coupleService.getConnectedCoupleId(userId);
        BucketItem item = bucketItemRepository.save(BucketItem.create(coupleId, userId,
            request.title().strip(), blankToNull(request.description()), request.targetDate()));
        return toResponse(item);
    }

    @Transactional(readOnly = true)
    public BucketItemListResponse list(UUID userId, BucketItemStatus status) {
        UUID coupleId = coupleService.getConnectedCoupleId(userId);
        List<BucketItem> items = switch (status) {
            case ALL -> bucketItemRepository.findByCoupleIdOrderByCompletedAscCreatedAtDesc(coupleId);
            case TODO -> bucketItemRepository.findByCoupleIdAndCompletedOrderByCreatedAtDesc(coupleId, false);
            case DONE -> bucketItemRepository.findByCoupleIdAndCompletedOrderByCreatedAtDesc(coupleId, true);
        };
        Map<UUID, User> users = loadUsers(items.stream().map(BucketItem::getCreatedBy).collect(Collectors.toSet()));
        return new BucketItemListResponse(items.stream().map(i -> toResponse(i, users)).toList());
    }

    @Transactional
    public BucketItemResponse update(UUID userId, UUID itemId, BucketItemUpdateRequest request) {
        BucketItem item = getItemInMyCouple(userId, itemId);
        item.update(request.title().strip(), blankToNull(request.description()), request.targetDate());
        return toResponse(item);
    }

    /** postId 가 있으면 우리 커플 글인지 확인 후 연결 */
    @Transactional
    public BucketItemResponse complete(UUID userId, UUID itemId, UUID postId) {
        BucketItem item = getItemInMyCouple(userId, itemId);
        if (postId != null) {
            postService.getPostInMyCouple(userId, postId);
        }
        item.complete(postId, clock.instant());
        return toResponse(item);
    }

    @Transactional
    public BucketItemResponse uncomplete(UUID userId, UUID itemId) {
        BucketItem item = getItemInMyCouple(userId, itemId);
        item.uncomplete();
        return toResponse(item);
    }

    @Transactional
    public void delete(UUID userId, UUID itemId) {
        bucketItemRepository.delete(getItemInMyCouple(userId, itemId));
    }

    private BucketItem getItemInMyCouple(UUID userId, UUID itemId) {
        UUID coupleId = coupleService.getConnectedCoupleId(userId);
        BucketItem item = bucketItemRepository.findById(itemId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "버킷리스트 항목을 찾을 수 없습니다."));
        if (!item.getCoupleId().equals(coupleId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "우리 커플의 버킷리스트가 아닙니다.");
        }
        return item;
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.strip() : null;
    }

    private Map<UUID, User> loadUsers(Collection<UUID> ids) {
        return userRepository.findAllById(ids).stream()
            .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private BucketItemResponse toResponse(BucketItem item) {
        return toResponse(item, loadUsers(List.of(item.getCreatedBy())));
    }

    private BucketItemResponse toResponse(BucketItem item, Map<UUID, User> users) {
        User creator = users.get(item.getCreatedBy());
        return new BucketItemResponse(
            item.getId(),
            item.getTitle(),
            item.getDescription(),
            item.getTargetDate(),
            item.isCompleted(),
            item.getCompletedPostId(),
            item.getCompletedAt(),
            new BucketItemResponse.Author(creator.getId(), creator.getNickname()),
            item.getCreatedAt()
        );
    }
}
