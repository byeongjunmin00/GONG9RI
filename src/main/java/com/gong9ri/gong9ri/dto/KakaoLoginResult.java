package com.gong9ri.gong9ri.dto;

import com.gong9ri.gong9ri.entity.Member;

/**
 * {@code MemberService.findOrCreateByKakao()}의 반환값 — 로그인/가입된 회원과 함께, 이미 연동된
 * 계정으로 재로그인하면서 {@code intendedRole}(로그인 진입 버튼이 넘긴 role)과 실제 회원 role이
 * 다른지 여부를 호출부(AuthController)에 알려준다. 로그인 자체는 항상 {@code member}의 기존 role
 * 그대로 진행되고({@code roleMismatch}는 안내 배너 표시 여부 판단에만 쓰인다) — role이 바뀌는 일은
 * 없다(docs/dev/auth/social-login/design.md).
 */
public record KakaoLoginResult(Member member, boolean roleMismatch) {
}
