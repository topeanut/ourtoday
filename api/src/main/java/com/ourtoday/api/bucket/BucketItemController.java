package com.ourtoday.api.bucket;

import com.ourtoday.api.bucket.dto.BucketItemCompleteRequest;
import com.ourtoday.api.bucket.dto.BucketItemCreateRequest;
import com.ourtoday.api.bucket.dto.BucketItemListResponse;
import com.ourtoday.api.bucket.dto.BucketItemResponse;
import com.ourtoday.api.bucket.dto.BucketItemUpdateRequest;
import com.ourtoday.api.common.guard.RequireCouple;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "BucketItem", description = "데이트 버킷리스트 (커플 둘 다 수정·삭제·완료 가능)")
@RestController
@RequestMapping("/api/v1/bucket-items")
@RequiredArgsConstructor
@RequireCouple
public class BucketItemController {

    private final BucketItemService bucketItemService;

    @Operation(summary = "추가")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BucketItemResponse create(@AuthenticationPrincipal UUID userId,
                                     @Valid @RequestBody BucketItemCreateRequest request) {
        return bucketItemService.create(userId, request);
    }

    @Operation(summary = "목록", description = "ALL 은 미완료 먼저, 각각 최신순")
    @GetMapping
    public BucketItemListResponse list(
        @AuthenticationPrincipal UUID userId,
        @Parameter(description = "ALL(기본) / TODO / DONE") @RequestParam(defaultValue = "ALL") BucketItemStatus status
    ) {
        return bucketItemService.list(userId, status);
    }

    @Operation(summary = "수정")
    @PatchMapping("/{id}")
    public BucketItemResponse update(@AuthenticationPrincipal UUID userId, @PathVariable UUID id,
                                     @Valid @RequestBody BucketItemUpdateRequest request) {
        return bucketItemService.update(userId, id, request);
    }

    @Operation(summary = "완료 처리", description = "postId 로 달성 게시글 연결 (선택). 이미 완료면 연결만 변경")
    @PatchMapping("/{id}/complete")
    public BucketItemResponse complete(@AuthenticationPrincipal UUID userId, @PathVariable UUID id,
                                       @RequestBody(required = false) BucketItemCompleteRequest request) {
        return bucketItemService.complete(userId, id, request == null ? null : request.postId());
    }

    @Operation(summary = "완료 취소")
    @DeleteMapping("/{id}/complete")
    public BucketItemResponse uncomplete(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        return bucketItemService.uncomplete(userId, id);
    }

    @Operation(summary = "삭제")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        bucketItemService.delete(userId, id);
    }
}
