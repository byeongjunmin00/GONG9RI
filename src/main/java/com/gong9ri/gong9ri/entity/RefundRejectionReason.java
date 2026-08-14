package com.gong9ri.gong9ri.entity;

/**
 * 판매자가 환불 요청을 거절할 때 고르는 사유 템플릿 — 자유 텍스트가 아니다(사용자 확인,
 * docs/dev/ongoing/team-leave-and-refund-request.md). 정확한 문구는 Generate가 정한다.
 */
public enum RefundRejectionReason {
    ALREADY_SHIPPED("상품이 이미 발송되어 환불이 어렵습니다."),
    ALREADY_USED("이미 사용/소비된 상품으로 환불이 어렵습니다."),
    POLICY_VIOLATION("환불 정책 상 요건을 충족하지 않아 환불이 어렵습니다."),
    OTHER("판매자 사정으로 환불 요청을 거절했습니다.");

    private final String description;

    RefundRejectionReason(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
