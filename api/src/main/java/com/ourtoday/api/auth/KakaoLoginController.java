package com.ourtoday.api.auth;

import com.ourtoday.api.auth.dto.KakaoPrepareResponse;
import com.ourtoday.api.common.error.BusinessException;
import com.ourtoday.api.common.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 휴대폰에서 카카오톡 앱으로 바로 로그인(카카오 JS SDK)할 때 쓰는 state 준비.
 *
 * <p>/oauth2/authorization/kakao 로 보내면 카카오가 웹 계정 로그인 화면으로만 보내서 카카오톡 앱이 열리지 않음.
 * 그래서 웹이 JS SDK(Kakao.Auth.authorize)로 카카오톡을 여는데, 돌아오는 콜백(/login/oauth2/code/kakao)은
 * Spring 이 세션에 저장한 state 를 확인하므로 같은 방식으로 미리 저장해 두고 state 만 넘겨줌.
 */
@Tag(name = "Auth")
@RestController
@RequestMapping("/api/v1/auth/kakao")
public class KakaoLoginController {

    private static final String REGISTRATION_ID = "kakao";

    private final OAuth2AuthorizationRequestResolver resolver;
    /** oauth2Login 기본 저장소와 같은 것 (세션 속성 키가 같아서 콜백 필터가 그대로 꺼내 씀) */
    private final AuthorizationRequestRepository<OAuth2AuthorizationRequest> repository =
        new HttpSessionOAuth2AuthorizationRequestRepository();

    public KakaoLoginController(ClientRegistrationRepository clientRegistrations) {
        this.resolver = new DefaultOAuth2AuthorizationRequestResolver(clientRegistrations, "/oauth2/authorization");
    }

    @Operation(summary = "카카오톡 앱 로그인 준비",
        description = "로그인 state 를 세션에 저장하고 state·redirectUri 를 돌려줌. 웹은 이 값으로 Kakao.Auth.authorize 를 호출")
    @PostMapping("/prepare")
    public ResponseEntity<KakaoPrepareResponse> prepare(HttpServletRequest request, HttpServletResponse response) {
        OAuth2AuthorizationRequest authRequest = resolver.resolve(request, REGISTRATION_ID);
        if (authRequest == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        repository.saveAuthorizationRequest(authRequest, request, response);
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(new KakaoPrepareResponse(authRequest.getState(), authRequest.getRedirectUri()));
    }
}
