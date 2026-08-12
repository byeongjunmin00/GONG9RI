package com.gong9ri.gong9ri.client;

/**
 * 카카오 로그인(OAuth2 Authorization Code) API 클라이언트 — 토큰 교환 + 사용자 정보 조회만 다룬다
 * (docs/dev/auth/social-login/design.md). 테스트에서는 항상 {@code @MockitoBean}으로 대체해 실제
 * 네트워크 호출을 하지 않는다({@code PortOneClient}와 같은 패턴).
 */
public interface KakaoClient {

    /**
     * {@code POST https://kauth.kakao.com/oauth/token} — 인가 코드를 액세스 토큰으로 교환한다.
     * {@code redirectUri}는 인가 요청({@code GET /api/auth/kakao/login}) 때 사용한 것과 정확히 같아야
     * 한다(카카오 API 요구사항) — 그래서 호출자가 직접 넘긴다.
     */
    String exchangeCodeForAccessToken(String code, String redirectUri);

    /**
     * {@code GET https://kapi.kakao.com/v2/user/me} — 액세스 토큰으로 사용자 정보를 조회한다.
     */
    KakaoUserInfo getUserInfo(String accessToken);
}
