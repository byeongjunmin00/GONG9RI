package com.gong9ri.gong9ri.client;

/**
 * 카카오 사용자 정보 조회 결과 중 회원 가입/로그인에 필요한 부분만 매핑한다. {@code email}은 이메일 동의
 * 항목을 사용자가 승인하지 않았거나 카카오 계정에 이메일이 없으면 {@code null}일 수 있다.
 */
public record KakaoUserInfo(Long id, String email, String nickname) {
}
