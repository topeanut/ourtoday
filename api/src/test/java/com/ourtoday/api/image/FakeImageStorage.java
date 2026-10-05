package com.ourtoday.api.image;

import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 테스트용 메모리 저장소. upload() 로 "업로드 완료" 상태를 흉내냄
 */
public class FakeImageStorage implements ImageStorage {

    private final Map<String, Long> objects = new ConcurrentHashMap<>();

    public void upload(String key, long size) {
        objects.put(key, size);
    }

    public boolean exists(String key) {
        return objects.containsKey(key);
    }

    public void clear() {
        objects.clear();
    }

    @Override
    public String presignUpload(String key, String contentType, long contentLength) {
        return "https://fake-storage/upload/" + key;
    }

    @Override
    public String presignDownload(String key) {
        return "https://fake-storage/download/" + key;
    }

    @Override
    public OptionalLong size(String key) {
        Long size = objects.get(key);
        return size == null ? OptionalLong.empty() : OptionalLong.of(size);
    }

    @Override
    public void delete(String key) {
        objects.remove(key);
    }
}
