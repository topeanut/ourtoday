package com.ourtoday.api.dev;

import com.ourtoday.api.image.ImageStorage;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 로컬 화면 점검용 메모리 저장소 (테스트 코드라 운영 빌드에 안 들어감).
 * presigned URL 대신 http://localhost:8080/dev-storage/{key} 로 PUT/GET → 브라우저에서 사진 업로드까지 확인 가능
 */
@Configuration
@Profile("seed")
class LocalDevStorage {

    private static final String PREFIX = "/dev-storage/";

    private record Obj(byte[] bytes, String contentType) {}

    private final Map<String, Obj> objects = new ConcurrentHashMap<>();
    /** 사진 주소 앞부분. 에뮬레이터에서 볼 때는 --dev.storage.base=http://10.0.2.2:8080/dev-storage/ */
    private final String base;

    LocalDevStorage(@org.springframework.beans.factory.annotation.Value(
        "${dev.storage.base:http://localhost:8080/dev-storage/}") String base) {
        this.base = base;
    }

    @Bean
    @Primary
    ImageStorage localImageStorage() {
        return new ImageStorage() {
            @Override
            public String presignUpload(String key, String contentType, long contentLength) {
                return base + key;
            }

            @Override
            public String presignDownload(String key) {
                return base + key;
            }

            @Override
            public OptionalLong size(String key) {
                Obj o = objects.get(key);
                return o == null ? OptionalLong.empty() : OptionalLong.of(o.bytes().length);
            }

            @Override
            public void delete(String key) {
                objects.remove(key);
            }

            @Override
            public void deleteByPrefix(String prefix) {
                objects.keySet().removeIf(k -> k.startsWith(prefix));
            }

            @Override
            public void put(String key, Path file, String contentType) {
                try {
                    objects.put(key, new Obj(Files.readAllBytes(file), contentType));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public void copyTo(String key, OutputStream out) {
                try {
                    out.write(objects.get(key).bytes());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public String presignAttachment(String key, String fileName) {
                return base + key;
            }
        };
    }

    /** 보안 필터보다 먼저 /dev-storage/* 를 직접 처리 (CORS 포함) */
    @Bean
    FilterRegistrationBean<OncePerRequestFilter> devStorageFilter() {
        OncePerRequestFilter filter = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                throws IOException {
                res.setHeader("Access-Control-Allow-Origin", "*");
                res.setHeader("Access-Control-Allow-Methods", "GET, PUT, OPTIONS");
                res.setHeader("Access-Control-Allow-Headers", "*");
                String key = req.getRequestURI().substring(PREFIX.length());
                switch (req.getMethod()) {
                    case "OPTIONS" -> res.setStatus(200);
                    case "PUT" -> {
                        objects.put(key, new Obj(req.getInputStream().readAllBytes(), req.getContentType()));
                        res.setStatus(200);
                    }
                    case "GET" -> {
                        Obj o = objects.get(key);
                        if (o == null) {
                            res.setStatus(404);
                            return;
                        }
                        res.setContentType(o.contentType() == null ? "application/octet-stream" : o.contentType());
                        res.getOutputStream().write(o.bytes());
                    }
                    default -> res.setStatus(405);
                }
            }
        };
        FilterRegistrationBean<OncePerRequestFilter> reg = new FilterRegistrationBean<>(filter);
        reg.addUrlPatterns(PREFIX + "*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return reg;
    }
}
