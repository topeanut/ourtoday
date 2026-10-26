package com.ourtoday.api.reaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReactionRepository extends JpaRepository<Reaction, UUID> {

    Optional<Reaction> findByPostIdAndUserIdAndEmojiType(UUID postId, UUID userId, EmojiType emojiType);

    void deleteByPostId(UUID postId);

    void deleteByUserId(UUID userId);

    /** 게시글별·이모지별 개수와 viewer 가 눌렀는지 — 목록도 한 번에 */
    @Query("""
        select new com.ourtoday.api.reaction.ReactionCount(
            r.postId, r.emojiType, count(r),
            sum(case when r.userId = :viewerId then 1 else 0 end))
        from Reaction r
        where r.postId in :postIds
        group by r.postId, r.emojiType
        """)
    List<ReactionCount> countByPostIds(@Param("postIds") Collection<UUID> postIds, @Param("viewerId") UUID viewerId);

    /** 우리 커플 글에 이 사용자가 남긴 반응 수 */
    @Query("""
        select count(r) from Reaction r, com.ourtoday.api.post.Post p
        where r.postId = p.id and p.coupleId = :coupleId and r.userId = :userId
        """)
    long countGivenInCouple(@Param("coupleId") UUID coupleId, @Param("userId") UUID userId);
}
