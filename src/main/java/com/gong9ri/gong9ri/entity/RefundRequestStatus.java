package com.gong9ri.gong9ri.entity;

/**
 * 환불 요청 상태 (docs/dev/ongoing/team-leave-and-refund-request.md).
 * <pre>
 * PENDING --(판매자 승인, 또는 상품별 "참여 취소 시 자동 환불" 설정이 켜져 있으면 즉시)--&gt; APPROVED
 *    |
 *    +--(판매자 거절, 사유 템플릿 선택)--&gt; REJECTED
 * </pre>
 * {@code APPROVED}는 "환불 실행이 확정됐다"는 뜻일 뿐, 실제 PortOne 결제취소 API 호출·완료는 별도
 * 비동기 이벤트({@code RefundRequestApprovedEvent})가 담당한다 — 결제(`Payment`)의 실제 상태 전이
 * (`PAID` → `REFUND_PENDING`/`REFUNDED`)는 `PaymentStatus`를 참고한다.
 */
public enum RefundRequestStatus {
    PENDING,
    APPROVED,
    REJECTED
}
