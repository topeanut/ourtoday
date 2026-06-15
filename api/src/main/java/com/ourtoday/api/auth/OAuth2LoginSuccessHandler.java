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

import java.io.IOException;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserService userService;
    private final JwtTokenProvider tokenProvider;
    private final ObjectMapper objectMapper;

    @Override
    public void onAuthenticationSuccess(
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication
    ) throws IOException {
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        String kakaoId = oauth2User.getAttributes().get("id").toString();

        User user = userService.findOrCreateByKakaoId(kakaoId);
        String accessToken = tokenProvider.generateAccessToken(user.getId());

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), Map.of(
            "accessToken", accessToken,
            "userId", user.getId().toString(),
            "profileCompleted", user.isProfileCompleted()
        ));
    }
}
