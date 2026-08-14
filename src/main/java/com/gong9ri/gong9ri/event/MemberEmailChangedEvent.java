package com.gong9ri.gong9ri.event;

/**
 * 마이페이지 정보수정에서 이메일이 실제로 바뀌었을 때만 발행 — {@code MemberEmailChangedEventListener}가
 * {@code MemberSignedUpEvent}와 동일한 인프라(토큰 발급+인증 메일 발송)로 재인증 메일을 보낸다.
 * 이름만 바뀌었거나 이메일을 기존 값 그대로 제출한 경우는 발행하지 않는다(불필요한 재인증 메일 방지).
 */
public record MemberEmailChangedEvent(Long memberId, String email) {
}
