package com.gong9ri.gong9ri.event;

import java.util.List;

/**
 * 공구팀 마감 처리({@code TeamDeadlineService.processDeadline})에서 환불이 발생했고, 그 트랜잭션이
 * "커밋된 이후에만" 발행돼야 하는 이벤트 — 커밋 전/롤백 시 발행되면 알림 정합성이 깨진다
 * (docs/dev/ongoing/refund-event-messaging.md). 발행 지점은 {@code @TransactionalEventListener(phase = AFTER_COMMIT)}로
 * 구독하는 {@code TeamRefundedEventListener}가 소비해 그 팀의 환불된 결제 구매자 전원 + 상품 판매자에게
 * 알림(Notification)을 생성한다.
 *
 * @param teamId 환불이 발생한 공구팀 id
 * @param sellerId 그 팀 상품의 판매자 member id
 * @param buyerMemberIds 환불된 결제들의 구매자 member id 목록(중복 가능 — 소비 측에서 중복 제거)
 */
public record TeamRefundedEvent(Long teamId, Long sellerId, List<Long> buyerMemberIds) {
}
