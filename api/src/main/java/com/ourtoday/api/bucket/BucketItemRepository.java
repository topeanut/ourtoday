package com.ourtoday.api.bucket;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface BucketItemRepository extends JpaRepository<BucketItem, UUID> {

    /** 미완료 먼저, 그 안에서 최신순 */
    List<BucketItem> findByCoupleIdOrderByCompletedAscCreatedAtDesc(UUID coupleId);

    List<BucketItem> findByCoupleIdAndCompletedOrderByCreatedAtDesc(UUID coupleId, boolean completed);

    /** 게시글 삭제 시 연결만 끊음 (완료 상태는 유지) */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update BucketItem b set b.completedPostId = null where b.completedPostId = :postId")
    void unlinkPost(@Param("postId") UUID postId);
}
