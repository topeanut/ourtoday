package com.ourtoday.api.image;

import java.io.OutputStream;
import java.nio.file.Path;
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

    /** 서버에서 직접 올림 (데이터 내보내기 zip 등) */
    void put(String key, Path file, String contentType);

    /** 객체 내용을 out 으로 복사 (out 은 닫지 않음) */
    void copyTo(String key, OutputStream out);

    /** 다운로드용 임시 URL. 브라우저가 fileName 으로 저장하도록 Content-Disposition 지정 */
    String presignAttachment(String key, String fileName);
}
