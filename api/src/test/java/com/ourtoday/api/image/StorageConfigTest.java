package com.ourtoday.api.image;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 키 없이 endpoint 만 있을 때 앱 기동이 실패했던 사고(2026-10-26) 회귀 방지
 */
class StorageConfigTest {

    private final StorageConfig config = new StorageConfig();

    @Test
    void endpoint_만_있고_키가_없으면_비활성_저장소() {
        ImageStorage storage = config.imageStorage(props("https://example.com", "", ""));

        assertThat(storage).isInstanceOf(StorageConfig.DisabledImageStorage.class);
    }

    @Test
    void 모두_있으면_S3_저장소() {
        ImageStorage storage = config.imageStorage(props("https://example.com", "access", "secret"));

        assertThat(storage).isInstanceOf(S3ImageStorage.class);
    }

    private StorageProperties props(String endpoint, String accessKey, String secretKey) {
        return new StorageProperties(endpoint, "ap-osaka-1", "bucket", accessKey, secretKey,
            Duration.ofMinutes(10), Duration.ofHours(1));
    }
}
