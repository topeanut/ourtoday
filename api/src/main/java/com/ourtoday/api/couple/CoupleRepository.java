package com.ourtoday.api.couple;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CoupleRepository extends JpaRepository<Couple, UUID> {

    boolean existsByInviteCode(String inviteCode);

    /** 같은 코드로 동시에 합류하는 경우 대비 행 잠금 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Couple c where c.inviteCode = :inviteCode")
    Optional<Couple> findByInviteCodeForUpdate(@Param("inviteCode") String inviteCode);

    @Query("""
        select c from Couple c
        where c.id in (select m.coupleId from CoupleMember m where m.userId = :userId)
        """)
    Optional<Couple> findByMemberUserId(@Param("userId") UUID userId);

    List<Couple> findByStatus(CoupleStatus status);
}
