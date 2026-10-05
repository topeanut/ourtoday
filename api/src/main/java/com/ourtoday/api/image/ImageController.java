package com.ourtoday.api.image;

import com.ourtoday.api.common.guard.RequireCouple;
import com.ourtoday.api.image.dto.PresignedUrlRequest;
import com.ourtoday.api.image.dto.PresignedUrlResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Image", description = "이미지 업로드")
@RestController
@RequestMapping("/api/v1/images")
@RequiredArgsConstructor
public class ImageController {

    private final ImageService imageService;

    @Operation(summary = "업로드 URL 발급",
        description = "uploadUrl 로 PUT 업로드(같은 Content-Type·Content-Length) 후 imageKey 를 게시글 imageKeys 에 넣기")
    @RequireCouple
    @PostMapping("/presigned-url")
    public PresignedUrlResponse presignedUrl(
        @AuthenticationPrincipal UUID userId,
        @Valid @RequestBody PresignedUrlRequest request
    ) {
        return imageService.createUploadUrl(userId, request);
    }
}
