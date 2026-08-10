package com.gong9ri.gong9ri.event;

/**
 * 스케줄러({@code TeamDeadlineScheduler})가 RECRUITING + deadline 지난 팀을 스캔해 팀 id별로 발행하는 이벤트.
 * 발행 시점에는 상태전환/환불을 직접 수행하지 않는다 — {@code TeamDeadlineEventListener}가 이 이벤트를
 * 구독해 기존 {@code TeamDeadlineService.processDeadline(teamId)} 로직을 수행한다
 * (docs/dev/ongoing/refund-event-messaging.md).
 */
public record TeamDeadlineDetectedEvent(Long teamId) {
}
