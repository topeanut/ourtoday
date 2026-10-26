package com.ourtoday.api.image;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 테스트용 메모리 저장소. upload() 로 "업로드 완료" 상태를 흉내냄
 */
public class FakeImageStorage implements ImageStorage {

    private final Map<String, Long> objects = new ConcurrentHashMap<>();
    private final Map<String, byte[]> contents = new ConcurrentHashMap<>();

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
        contents.remove(key);
    }

    @Override
    public void deleteByPrefix(String prefix) {
        objects.keySet().removeIf(k -> k.startsWith(prefix));
        contents.keySet().removeIf(k -> k.startsWith(prefix));
    }

    @Override
    public void put(String key, Path file, String contentType) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            contents.put(key, bytes);
            objects.put(key, (long) bytes.length);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void copyTo(String key, OutputStream out) {
        try {
            out.write(contents.getOrDefault(key, new byte[objects.getOrDefault(key, 0L).intValue()]));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String presignAttachment(String key, String fileName) {
        return "https://fake-storage/download/" + key + "?filename=" + fileName;
    }

    public byte[] content(String key) {
        return contents.get(key);
    }
}
