package com.ourtoday.api.image;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * S3 호환 저장소 설정 (운영: Cloudflare R2)
 *
 * @param endpoint       예: https://{accountId}.r2.cloudflarestorage.com. 비우면 이미지 기능 비활성
 * @param uploadUrlTtl   업로드용 presigned PUT 유효시간
 * @param downloadUrlTtl 조회용 presigned GET 유효시간
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
    String endpoint,
    String region,
    String bucket,
    String accessKey,
    String secretKey,
    Duration uploadUrlTtl,
    Duration downloadUrlTtl
) {}
