package com.gong9ri.gong9ri.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * 카카오 로그인 REST API 실제 호출 구현. 별도 SDK 의존성 없이 {@code RestClient}(spring-web 내장)로
 * 직접 호출한다 — {@code PortOneApiClient}와 같은 판단(공식 SDK 없이도 REST 호출로 충분히 단순함).
 */
@Component
public class KakaoApiClient implements KakaoClient {

    private final RestClient authClient;
    private final RestClient apiClient;
    private final String clientId;
    private final String clientSecret;

    public KakaoApiClient(
            @Value("${kakao.client-id}") String clientId,
            @Value("${kakao.client-secret}") String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.authClient = RestClient.builder().baseUrl("https://kauth.kakao.com").build();
        this.apiClient = RestClient.builder().baseUrl("https://kapi.kakao.com").build();
    }

    @Override
    public String exchangeCodeForAccessToken(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", clientId);
        form.add("redirect_uri", redirectUri);
        form.add("code", code);
        if (clientSecret != null && !clientSecret.isBlank()) {
            form.add("client_secret", clientSecret);
        }

        TokenResponse response = authClient.post()
                .uri("/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);
        return response != null ? response.accessToken() : null;
    }

    @Override
    public KakaoUserInfo getUserInfo(String accessToken) {
        UserInfoResponse response = apiClient.get()
                .uri("/v2/user/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(UserInfoResponse.class);
        if (response == null) {
            return null;
        }
        String email = response.kakaoAccount() != null ? response.kakaoAccount().email() : null;
        String nickname = response.kakaoAccount() != null && response.kakaoAccount().profile() != null
                ? response.kakaoAccount().profile().nickname()
                : null;
        return new KakaoUserInfo(response.id(), email, nickname);
    }

    // 카카오 응답 스키마 중 로그인/가입에 필요한 부분만 매핑한다(그 외 필드는 무시). 카카오는 필드명이
    // snake_case라 카멜케이스 레코드 컴포넌트에 @JsonProperty로 명시적으로 매핑한다(project-wide 네이밍
    // 전략 설정이 없어서 자동 변환에 기대지 않음).
    private record TokenResponse(@JsonProperty("access_token") String accessToken) {
    }

    private record UserInfoResponse(Long id, @JsonProperty("kakao_account") KakaoAccount kakaoAccount) {
        private record KakaoAccount(String email, Profile profile) {
            private record Profile(String nickname) {
            }
        }
    }
}
