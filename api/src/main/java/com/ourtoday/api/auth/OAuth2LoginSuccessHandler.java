package com.ourtoday.api.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ourtoday.api.user.User;
import com.ourtoday.api.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserService userService;
    private final JwtTokenProvider tokenProvider;
    private final ObjectMapper objectMapper;
    private final OAuth2Properties oauth2Properties;

    @Override
    public void onAuthenticationSuccess(
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication
    ) throws IOException {
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        String kakaoId = oauth2User.getAttributes().get("id").toString();

        User user = userService.findOrCreateByKakaoId(kakaoId);
        Map<String, Object> attrs = oauth2User.getAttributes();
        userService.syncKakaoProfile(user.getId(), KakaoProfile.imageUrl(attrs),
            KakaoProfile.nickname(attrs, User.NICKNAME_MAX_LENGTH));
        String accessToken = tokenProvider.generateAccessToken(user.getId());
        String refreshToken = tokenProvider.generateRefreshToken(user.getId());

        Map<String, String> result = Map.of(
            "accessToken", accessToken,
            "refreshToken", refreshToken,
            "userId", user.getId().toString(),
            "profileCompleted", String.valueOf(user.isProfileCompleted())
        );

        // OAuth2 state 용 세션은 여기서 역할 끝
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }

        String redirectUri = oauth2Properties.successRedirectUri();
        if (StringUtils.hasText(redirectUri)) {
            // fragment(#) 로 전달 → 서버/프록시 access log 에 토큰이 남지 않음
            response.sendRedirect(redirectUri + "#" + toQueryString(result));
            return;
        }

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), Map.of(
            "accessToken", accessToken,
            "refreshToken", refreshToken,
            "userId", user.getId().toString(),
            "profileCompleted", user.isProfileCompleted()
        ));
    }

    private String toQueryString(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        params.forEach((k, v) -> {
            if (!sb.isEmpty()) sb.append('&');
            sb.append(k).append('=').append(URLEncoder.encode(v, StandardCharsets.UTF_8));
        });
        return sb.toString();
    }
}
