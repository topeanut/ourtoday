package com.ourtoday.api.image;

import org.junit.jupiter.api.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

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

    @Test
    void 조회_URL은_브라우저_캐시_헤더를_서명에_포함() {
        String url = URLDecoder.decode(storage.presignDownload("couples/c/posts/p.jpg"), StandardCharsets.UTF_8);

        assertThat(url).contains("response-cache-control=private, max-age=3600, immutable");
    }

    @Test
    void 같은_키는_남은시간이_충분하면_같은_URL_만료가_가까우면_새로_발급() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-25T00:00:00Z"));
        S3ImageStorage cached = new S3ImageStorage(new StorageProperties(
            "https://test-account.r2.cloudflarestorage.com", "auto", "ourtoday-images",
            "test-access", "test-secret", Duration.ofMinutes(10), Duration.ofHours(1)), clock);

        String first = cached.presignDownload("couples/c/posts/p.jpg");
        clock.advance(Duration.ofMinutes(44));
        assertThat(cached.presignDownload("couples/c/posts/p.jpg")).isSameAs(first);
        assertThat(cached.presignDownload("couples/c/posts/other.jpg")).isNotEqualTo(first);

        // 서명 시각은 실제 시계라 문자열이 같을 수 있음 → 캐시가 아닌 새 발급인지 객체 동일성으로 확인
        clock.advance(Duration.ofMinutes(2));
        assertThat(cached.presignDownload("couples/c/posts/p.jpg")).isNotSameAs(first);
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
