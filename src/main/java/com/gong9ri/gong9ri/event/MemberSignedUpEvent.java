package com.gong9ri.gong9ri.event;

/**
 * 회원가입 성공 시 발행 — {@code MemberSignedUpEventListener}가 이메일 인증 토큰을 발급하고
 * 인증 메일을 보낸다. email을 이벤트에 같이 담아서 리스너가 재조회 없이 바로 메일을 보낼 수 있게 한다
 * ({@code TeamRefundedEvent}가 buyerMemberIds를 직접 담는 것과 같은 결).
 */
public record MemberSignedUpEvent(Long memberId, String email) {
}
