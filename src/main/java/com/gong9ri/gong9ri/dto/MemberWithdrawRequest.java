package com.gong9ri.gong9ri.dto;

/**
 * 회원 탈퇴 요청 (member/withdraw, 2026-08-21).
 *
 * <p>비밀번호는 <b>본인 확인용</b>이다 — 되돌릴 수 없는 동작이라 로그인 상태만으로는 부족하다고 봤다.
 * 카카오 계정은 비밀번호가 없으므로(가입 시 랜덤 값으로 채운다) null이어도 되고, 서버가 소셜 계정일
 * 때만 이 검사를 건너뛴다. 그래서 {@code @NotBlank}를 걸지 않는다.
 */
public record MemberWithdrawRequest(String password) {
}
