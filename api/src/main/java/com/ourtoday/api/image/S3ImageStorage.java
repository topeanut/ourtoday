package com.ourtoday.api.image;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;

import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;

public class S3ImageStorage implements ImageStorage {

    /** 이 개수를 넘으면 만료된 캐시 항목을 정리 (커플 앱이라 사진 수가 많지 않음) */
    private static final int CACHE_CLEANUP_THRESHOLD = 5_000;

    private final StorageProperties props;
    private final S3Client client;
    private final S3Presigner presigner;
    private final Clock clock;
    /**
     * 조회 URL 캐시. 서명 URL 은 발급 시각이 들어가 매번 달라지므로, 그대로 두면
     * 피드를 새로 받을 때마다 브라우저 캐시가 빗나가 사진을 전부 다시 받음 → 남은 시간이 충분하면 같은 URL 재사용
     */
    private final Map<String, CachedUrl> downloadUrls = new ConcurrentHashMap<>();

    private record CachedUrl(String url, Instant expiresAt) {}

    public S3ImageStorage(StorageProperties props) {
        this(props, Clock.systemUTC());
    }

    S3ImageStorage(StorageProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
        var credentials = StaticCredentialsProvider.create(
            AwsBasicCredentials.create(props.accessKey(), props.secretKey()));
        var endpoint = URI.create(props.endpoint());
        var region = Region.of(props.region());
        // R2 는 path-style 권장
        var s3Config = S3Configuration.builder().pathStyleAccessEnabled(true).build();

        this.client = S3Client.builder()
            .endpointOverride(endpoint).region(region).credentialsProvider(credentials)
            .serviceConfiguration(s3Config)
            .build();
        this.presigner = S3Presigner.builder()
            .endpointOverride(endpoint).region(region).credentialsProvider(credentials)
            .serviceConfiguration(s3Config)
            .build();
    }

    @Override
    public String presignUpload(String key, String contentType, long contentLength) {
        PutObjectRequest put = PutObjectRequest.builder()
            .bucket(props.bucket()).key(key)
            .contentType(contentType).contentLength(contentLength)
            .build();
        return presigner.presignPutObject(r -> r.signatureDuration(props.uploadUrlTtl()).putObjectRequest(put))
            .url().toString();
    }

    /**
     * 남은 유효시간이 전체의 1/4 이상이면 캐시된 URL 을 그대로 돌려줌 (1시간 TTL 이면 약 45분간 같은 URL).
     * 키는 UUID 라 내용이 바뀌지 않으므로 응답에 Cache-Control 을 붙여 브라우저가 URL 유효시간 동안 재사용하게 함
     */
    @Override
    public String presignDownload(String key) {
        Instant now = clock.instant();
        Duration ttl = props.downloadUrlTtl();
        CachedUrl cached = downloadUrls.get(key);
        if (cached != null && cached.expiresAt().minus(ttl.dividedBy(4)).isAfter(now)) {
            return cached.url();
        }
        GetObjectRequest get = GetObjectRequest.builder().bucket(props.bucket()).key(key)
            .responseCacheControl("private, max-age=" + ttl.toSeconds() + ", immutable")
            .build();
        String url = presigner.presignGetObject(r -> r.signatureDuration(ttl).getObjectRequest(get)).url().toString();
        if (downloadUrls.size() > CACHE_CLEANUP_THRESHOLD) {
            downloadUrls.values().removeIf(c -> !c.expiresAt().isAfter(now));
        }
        downloadUrls.put(key, new CachedUrl(url, now.plus(ttl)));
        return url;
    }

    @Override
    public OptionalLong size(String key) {
        try {
            return OptionalLong.of(client.headObject(
                HeadObjectRequest.builder().bucket(props.bucket()).key(key).build()).contentLength());
        } catch (NoSuchKeyException e) {
            return OptionalLong.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return OptionalLong.empty();
            }
            throw e;
        }
    }

    @Override
    public void delete(String key) {
        downloadUrls.remove(key);
        client.deleteObject(DeleteObjectRequest.builder().bucket(props.bucket()).key(key).build());
    }

    @Override
    public void deleteByPrefix(String prefix) {
        client.listObjectsV2Paginator(ListObjectsV2Request.builder().bucket(props.bucket()).prefix(prefix).build())
            .contents().stream()
            .map(S3Object::key)
            .forEach(this::delete);
    }

    @Override
    public void put(String key, Path file, String contentType) {
        client.putObject(PutObjectRequest.builder().bucket(props.bucket()).key(key).contentType(contentType).build(),
            RequestBody.fromFile(file));
    }

    @Override
    public void copyTo(String key, OutputStream out) {
        client.getObject(GetObjectRequest.builder().bucket(props.bucket()).key(key).build(),
            ResponseTransformer.toOutputStream(out));
    }

    @Override
    public String presignAttachment(String key, String fileName) {
        GetObjectRequest get = GetObjectRequest.builder().bucket(props.bucket()).key(key)
            .responseContentDisposition("attachment; filename=\"" + fileName + "\"")
            .build();
        return presigner.presignGetObject(r -> r.signatureDuration(props.downloadUrlTtl()).getObjectRequest(get))
            .url().toString();
    }
}
