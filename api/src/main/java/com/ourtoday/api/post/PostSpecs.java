package com.ourtoday.api.post;

import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 목록 필터. null 조건은 건너뜀 (JPQL 의 null 파라미터 타입 추론 문제 회피)
 */
final class PostSpecs {

    private PostSpecs() {
    }

    static Specification<Post> inCouple(UUID coupleId) {
        return (root, query, cb) -> cb.equal(root.get("coupleId"), coupleId);
    }

    static Specification<Post> writtenBy(UUID authorId) {
        return authorId == null ? null : (root, query, cb) -> cb.equal(root.get("authorId"), authorId);
    }

    static Specification<Post> postedFrom(LocalDate from) {
        return from == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("postedDate"), from);
    }

    static Specification<Post> postedTo(LocalDate to) {
        return to == null ? null : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("postedDate"), to);
    }
}
