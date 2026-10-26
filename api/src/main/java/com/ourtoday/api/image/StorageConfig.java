package com.ourtoday.api.image;

import com.ourtoday.api.common.error.BusinessException;
import com.ourtoday.api.common.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.io.OutputStream;
import java.nio.file.Path;
import java.util.OptionalLong;

@Slf4j
@Configuration
public class StorageConfig {

    @Bean
    public ImageStorage imageStorage(StorageProperties props) {
        // 설정이 하나라도 비면 앱 기동은 막지 않고 이미지 기능만 끔
        if (!StringUtils.hasText(props.endpoint())
            || !StringUtils.hasText(props.accessKey())
            || !StringUtils.hasText(props.secretKey())) {
            log.warn("app.storage endpoint/access-key/secret-key 중 미설정 → 이미지 기능 비활성");
            return new DisabledImageStorage();
        }
        return new S3ImageStorage(props);
    }

    /** 로컬 개발 등 저장소 없이 띄울 때. 이미지 API 호출 시에만 에러 */
    static class DisabledImageStorage implements ImageStorage {

        @Override
        public String presignUpload(String key, String contentType, long contentLength) {
            throw disabled();
        }

        @Override
        public String presignDownload(String key) {
            throw disabled();
        }

        @Override
        public OptionalLong size(String key) {
            throw disabled();
        }

        @Override
        public void delete(String key) {
            throw disabled();
        }

        @Override
        public void deleteByPrefix(String prefix) {
            // 저장소가 없으면 지울 것도 없음
        }

        @Override
        public void put(String key, Path file, String contentType) {
            throw disabled();
        }

        @Override
        public void copyTo(String key, OutputStream out) {
            throw disabled();
        }

        @Override
        public String presignAttachment(String key, String fileName) {
            throw disabled();
        }

        private BusinessException disabled() {
            return new BusinessException(ErrorCode.INTERNAL_ERROR, "이미지 저장소가 설정되지 않았습니다.");
        }
    }
}
