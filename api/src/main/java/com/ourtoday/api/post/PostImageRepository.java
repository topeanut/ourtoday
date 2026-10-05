package com.ourtoday.api.post;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PostImageRepository extends JpaRepository<PostImage, UUID> {

    List<PostImage> findByPostIdInOrderByDisplayOrderAsc(Collection<UUID> postIds);

    List<PostImage> findByPostIdOrderByDisplayOrderAsc(UUID postId);

    void deleteByPostId(UUID postId);
}
