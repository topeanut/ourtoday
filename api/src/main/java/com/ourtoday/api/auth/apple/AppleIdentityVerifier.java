package com.ourtoday.api.auth.apple;

/** Apple identity token(JWT) 검증. 실패하면 예외 */
public interface AppleIdentityVerifier {

    AppleIdentity verify(String identityToken);
}
