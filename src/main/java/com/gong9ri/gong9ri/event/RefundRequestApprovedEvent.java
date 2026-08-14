package com.gong9ri.gong9ri.event;

/**
 * 환불 요청이 승인됐다(판매자 수동 승인, 또는 상품별 "참여 취소 시 자동 환불" 설정이 켜져 있어 즉시
 * 승인된 경우 모두 포함) — 실제 PortOne 결제취소 API 호출은 이 이벤트를 커밋 이후 구독하는
 * {@code RefundRequestApprovedEventListener}가 담당한다(docs/dev/ongoing/team-leave-and-refund-request.md).
 *
 * @param refundRequestId 승인된 환불 요청 id(로깅용)
 * @param paymentId 취소 대상 결제 id
 * @param reason PortOne 취소 API에 보낼 사유 문구
 */
public record RefundRequestApprovedEvent(Long refundRequestId, Long paymentId, String reason) {
}
