package com.ourtoday.api.couple;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CoupleMemberRepository extends JpaRepository<CoupleMember, CoupleMember.Pk> {

    List<CoupleMember> findByCoupleIdOrderByJoinedAtAsc(UUID coupleId);

    void deleteByCoupleId(UUID coupleId);
}
