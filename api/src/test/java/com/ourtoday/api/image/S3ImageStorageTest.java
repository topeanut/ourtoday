package com.ourtoday.api.image;

import org.junit.jupiter.api.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * presign 은 네트워크 없이 서명만 만듦 → R2 연결 없이 검증 가능
 */
class S3ImageStorageTest {

    private final S3ImageStorage storage = new S3ImageStorage(new StorageProperties(
        "https://test-account.r2.cloudflarestorage.com", "auto", "ourtoday-images",
        "test-access", "test-secret", Duration.ofMinutes(10), Duration.ofHours(1)));

    @Test
    void 업로드_URL은_버킷경로_10분_유효_타입과_크기가_서명에_포함() {
        String url = URLDecoder.decode(
            storage.presignUpload("couples/c/posts/p.jpg", "image/jpeg", 1234), StandardCharsets.UTF_8);

        assertThat(url).startsWith("https://test-account.r2.cloudflarestorage.com/ourtoday-images/couples/c/posts/p.jpg?");
        assertThat(url).contains("X-Amz-Expires=600");
        assertThat(url).containsPattern("X-Amz-SignedHeaders=[^&]*content-length");
        assertThat(url).containsPattern("X-Amz-SignedHeaders=[^&]*content-type");
    }

    @Test
    void 조회_URL은_1시간_유효() {
        String url = storage.presignDownload("couples/c/posts/p.jpg");

        assertThat(url).contains("X-Amz-Expires=3600");
    }
}
