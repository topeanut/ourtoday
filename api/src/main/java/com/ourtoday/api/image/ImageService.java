package com.ourtoday.api.image;

import com.ourtoday.api.common.error.BusinessException;
import com.ourtoday.api.common.error.ErrorCode;
import com.ourtoday.api.couple.CoupleService;
import com.ourtoday.api.image.dto.PresignedUrlRequest;
import com.ourtoday.api.image.dto.PresignedUrlResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageService {

    public static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final String THUMBNAIL_CONTENT_TYPE = "image/jpeg";
    private static final Map<String, String> EXTENSIONS = Map.of(
        "image/jpeg", "jpg",
        "image/png", "png",
        "image/webp", "webp",
        "image/heic", "heic",
        "image/heif", "heif"
    );
    private static final Pattern KEY_PATTERN =
        Pattern.compile("^couples/[0-9a-f-]{36}/posts/[0-9a-f-]{36}\\.(jpg|png|webp|heic|heif)$");
    private static final Pattern PROFILE_KEY_PATTERN =
        Pattern.compile("^users/[0-9a-f-]{36}/profile/[0-9a-f-]{36}\\.(jpg|png|webp|heic|heif)$");

    private final ImageStorage storage;
    private final StorageProperties props;
    private final CoupleService coupleService;

    public PresignedUrlResponse createUploadUrl(UUID userId, PresignedUrlRequest request) {
        UUID coupleId = coupleService.getConnectedCoupleId(userId);
        PresignedUrlResponse original = presign(prefixOf(coupleId), request);
        if (request.thumbnailContentLength() == null) {
            return original;
        }
        String thumbnailUrl = storage.presignUpload(
            thumbnailKeyOf(original.imageKey()), THUMBNAIL_CONTENT_TYPE, request.thumbnailContentLength());
        return new PresignedUrlResponse(original.uploadUrl(), original.imageKey(), original.expiresInSec(), thumbnailUrl);
    }

    /**
     * 게시글 사진의 썸네일 키: couples/{c}/posts/{id}.jpg → couples/{c}/posts/{id}_thumb.jpg.
     * KEY_PATTERN 에 안 맞으므로 썸네일 키를 게시글 imageKeys 로 붙일 수는 없음
     */
    public static String thumbnailKeyOf(String imageKey) {
        int dot = imageKey.lastIndexOf('.');
        return imageKey.substring(0, dot) + "_thumb.jpg";
    }

    /** 썸네일이 실제로 올라갔으면 그 키, 아니면 null (예전 글·썸네일 업로드 실패 → 화면은 원본 사용) */
    public String uploadedThumbnailKey(String imageKey) {
        String key = thumbnailKeyOf(imageKey);
        OptionalLong size = storage.size(key);
        return size.isPresent() && size.getAsLong() <= MAX_BYTES ? key : null;
    }

    /** 프로필 사진: 커플 연결 전(가입 중)에도 올릴 수 있게 사용자 경로에 저장 */
    public PresignedUrlResponse createProfileUploadUrl(UUID userId, PresignedUrlRequest request) {
        return presign(profilePrefixOf(userId), request);
    }

    /** 프로필로 지정하기 전 검증: 내 프로필 경로의 키인지, 실제 업로드됐는지, 크기 제한 이내인지 */
    public void verifyProfileKey(UUID userId, String key) {
        if (!PROFILE_KEY_PATTERN.matcher(key).matches() || !key.startsWith(profilePrefixOf(userId))) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "사용할 수 없는 profileImageKey 입니다: " + key);
        }
        verifyUploaded(key);
    }

    public static String userPrefixOf(UUID userId) {
        return "users/" + userId + "/";
    }

    private static String profilePrefixOf(UUID userId) {
        return userPrefixOf(userId) + "profile/";
    }

    private PresignedUrlResponse presign(String prefix, PresignedUrlRequest request) {
        String contentType = request.contentType().strip().toLowerCase();
        String ext = EXTENSIONS.get(contentType);
        if (ext == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "지원하지 않는 이미지 형식입니다: " + contentType);
        }
        String key = prefix + UUID.randomUUID() + "." + ext;
        String url = storage.presignUpload(key, contentType, request.contentLength());
        return new PresignedUrlResponse(url, key, props.uploadUrlTtl().toSeconds(), null);
    }

    private void verifyUploaded(String key) {
        OptionalLong size = storage.size(key);
        if (size.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "업로드되지 않은 이미지입니다: " + key);
        }
        if (size.getAsLong() > MAX_BYTES) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "이미지는 10MB 이하만 가능합니다: " + key);
        }
    }

    /**
     * 게시글에 붙이기 전 검증: 우리 커플 경로의 키인지, 실제 업로드됐는지, 크기 제한 이내인지
     */
    public void verifyAttachable(UUID coupleId, List<String> keys) {
        String prefix = prefixOf(coupleId);
        for (String key : keys) {
            if (!KEY_PATTERN.matcher(key).matches() || !key.startsWith(prefix)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "사용할 수 없는 imageKey 입니다: " + key);
            }
            verifyUploaded(key);
        }
    }

    public String downloadUrl(String key) {
        return storage.presignDownload(key);
    }

    /** DB 커밋이 끝난 뒤 저장소에서 삭제 (롤백 시 파일은 남김). 실패해도 요청은 성공 처리 */
    public void deleteAfterCommit(Collection<String> keys) {
        if (keys.isEmpty()) {
            return;
        }
        List<String> targets = List.copyOf(keys);
        Runnable task = () -> targets.forEach(key -> {
            try {
                storage.delete(key);
            } catch (RuntimeException e) {
                log.warn("이미지 삭제 실패 key={}", key, e);
            }
        });
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    /** 커밋 후 prefix 아래 파일 삭제. 실패해도 요청은 성공 처리 */
    public void deletePrefixAfterCommit(String prefix) {
        Runnable task = () -> {
            try {
                storage.deleteByPrefix(prefix);
            } catch (RuntimeException e) {
                log.warn("저장소 정리 실패 prefix={}", prefix, e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    private String prefixOf(UUID coupleId) {
        return "couples/" + coupleId + "/posts/";
    }
}
