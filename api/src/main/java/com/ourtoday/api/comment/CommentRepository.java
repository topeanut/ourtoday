package com.ourtoday.api.comment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    List<Comment> findByPostIdOrderByCreatedAtAsc(UUID postId);

    List<Comment> findByPostIdInOrderByCreatedAtAsc(Collection<UUID> postIds);

    void deleteByPostId(UUID postId);

    void deleteByAuthorId(UUID authorId);
}
