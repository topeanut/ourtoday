package com.ourtoday.api.post;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID>, JpaSpecificationExecutor<Post> {

    /** 작년 오늘: 같은 월/일, 올해 이전 */
    @Query("""
        select p from Post p
        where p.coupleId = :coupleId
          and extract(month from p.postedDate) = :month
          and extract(day from p.postedDate) = :day
          and p.postedDate < :before
        order by p.postedDate desc, p.createdAt asc
        """)
    List<Post> findMemories(@Param("coupleId") UUID coupleId, @Param("month") int month, @Param("day") int day,
                            @Param("before") LocalDate before);

    long countByCoupleIdAndAuthorId(UUID coupleId, UUID authorId);

    List<Post> findByCoupleIdOrderByPostedDateAscCreatedAtAsc(UUID coupleId);

    List<Post> findByAuthorId(UUID authorId);

    long countByCoupleIdAndAuthorIdAndPostedDateBetween(UUID coupleId, UUID authorId, LocalDate from, LocalDate to);

    /** 연속 작성일 계산용: 기준일 이전 작성 날짜 (최신순, 중복 제거) */
    @Query("""
        select distinct p.postedDate from Post p
        where p.coupleId = :coupleId and p.authorId = :authorId and p.postedDate <= :until
        order by p.postedDate desc
        """)
    List<LocalDate> findPostedDatesDesc(@Param("coupleId") UUID coupleId, @Param("authorId") UUID authorId,
                                        @Param("until") LocalDate until);

    @Query("select min(p.postedDate) from Post p where p.coupleId = :coupleId")
    Optional<LocalDate> findFirstPostedDate(@Param("coupleId") UUID coupleId);
}
