package com.gong9ri.gong9ri.event;

import java.util.List;

/**
 * 공구팀 마감 처리({@code TeamDeadlineService.processDeadline})에서 환불 대상 결제를 찾았고, 그
 * 트랜잭션이 "커밋된 이후에만" 소비돼야 하는 이벤트다. {@code processDeadline}은 이 시점에 결제 상태를
 * 바꾸지 않는다 — 실제 PortOne 결제취소 API 호출(외부 HTTP, 지연 가능)을 비관적 락({@code
 * findByIdForUpdate}) 트랜잭션 안에서 하면 락을 오래 잡게 되므로(docs/dev/payment-portone.md 브리핑),
 * 취소 호출과 그 결과에 따른 상태 전환은 이 이벤트를 커밋 이후에 구독하는
 * {@code TeamPaymentsRefundRequestedEventListener} → {@code PaymentRefundService}가 담당한다.
 *
 * @param teamId 마감 처리된 공구팀 id(로깅용)
 * @param paymentIds 환불(취소) 대상 결제 id 목록(스캔 시점 기준 PAID였던 결제들)
 */
public record TeamPaymentsRefundRequestedEvent(Long teamId, List<Long> paymentIds) {
}
