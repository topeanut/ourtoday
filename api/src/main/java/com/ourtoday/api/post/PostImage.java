package com.ourtoday.api.post;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 게시글 이미지. 실제 파일은 비공개 저장소에, 여기엔 객체 키만
 */
@Entity
@Table(name = "post_images", indexes = @Index(name = "idx_post_images_post", columnList = "post_id, display_order"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostImage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Column(name = "image_key", nullable = false, length = 300)
    private String imageKey;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public static PostImage of(UUID postId, String imageKey, int displayOrder) {
        PostImage image = new PostImage();
        image.postId = postId;
        image.imageKey = imageKey;
        image.displayOrder = displayOrder;
        return image;
    }
}
