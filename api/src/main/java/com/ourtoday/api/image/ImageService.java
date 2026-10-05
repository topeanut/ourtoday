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
    private static final Map<String, String> EXTENSIONS = Map.of(
        "image/jpeg", "jpg",
        "image/png", "png",
        "image/webp", "webp",
        "image/heic", "heic",
        "image/heif", "heif"
    );
    private static final Pattern KEY_PATTERN =
        Pattern.compile("^couples/[0-9a-f-]{36}/posts/[0-9a-f-]{36}\\.(jpg|png|webp|heic|heif)$");

    private final ImageStorage storage;
    private final StorageProperties props;
    private final CoupleService coupleService;

    public PresignedUrlResponse createUploadUrl(UUID userId, PresignedUrlRequest request) {
        String contentType = request.contentType().strip().toLowerCase();
        String ext = EXTENSIONS.get(contentType);
        if (ext == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "지원하지 않는 이미지 형식입니다: " + contentType);
        }
        UUID coupleId = coupleService.getConnectedCoupleId(userId);
        String key = prefixOf(coupleId) + UUID.randomUUID() + "." + ext;
        String url = storage.presignUpload(key, contentType, request.contentLength());
        return new PresignedUrlResponse(url, key, props.uploadUrlTtl().toSeconds());
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
            OptionalLong size = storage.size(key);
            if (size.isEmpty()) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "업로드되지 않은 이미지입니다: " + key);
            }
            if (size.getAsLong() > MAX_BYTES) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "이미지는 10MB 이하만 가능합니다: " + key);
            }
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

    private String prefixOf(UUID coupleId) {
        return "couples/" + coupleId + "/posts/";
    }
}
