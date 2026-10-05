package com.ourtoday.api.image;

import java.util.OptionalLong;

/**
 * 비공개 버킷 + presigned URL 로만 접근
 */
public interface ImageStorage {

    /** 클라이언트가 직접 PUT 할 URL. contentType·contentLength 가 서명에 포함돼 다르면 거부됨 */
    String presignUpload(String key, String contentType, long contentLength);

    /** 조회용 임시 URL */
    String presignDownload(String key);

    /** 업로드된 객체 크기. 없으면 empty */
    OptionalLong size(String key);

    void delete(String key);
}
