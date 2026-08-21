package com.gong9ri.gong9ri.entity;

/**
 * 알림 종류. 값 이름은 "무슨 일이 일어났는가"(사건) 기준이며, 수신자(구매자/판매자)는 이름에 넣지 않는다 —
 * 같은 사건을 양쪽이 함께 받는 경우가 있기 때문이다(예: 공구팀 성사는 참여자 전원 + 판매자가 같이 받는다).
 * 수신자별로 달라지는 건 문구뿐이라 {@code NotificationService}가 메시지를 나눠 쓴다.
 */
public enum NotificationType {

    /** 공구팀이 미성사돼 환불 처리됨 — 그 팀의 환불된 결제 구매자 + 상품 판매자. */
    TEAM_REFUNDED,

    /** 공구팀이 정원을 채워 성사됨 — 참여자 전원 + 상품 판매자. */
    TEAM_SUCCESS,

    /** 상품에 문의가 등록됨 — 상품 판매자. */
    INQUIRY_CREATED,

    /** 문의에 답변이 등록됨 — 문의 작성자. */
    INQUIRY_ANSWERED,

    /** 상품이 결제됨 — 상품 판매자. */
    PAYMENT_RECEIVED,

    /** 상품에 리뷰가 등록됨 — 상품 판매자. */
    REVIEW_CREATED,

    /** 환불 요청이 접수됨 — 상품 판매자(승인/거절 처리가 필요하다). */
    REFUND_REQUESTED,

    /** 환불 요청이 승인됨 — 요청한 구매자. */
    REFUND_REQUEST_APPROVED,

    /** 환불 요청이 거절됨 — 요청한 구매자. */
    REFUND_REQUEST_REJECTED,

    /**
     * 고객센터 상담에 새 메시지가 도착함 — 관리자.
     *
     * <p><b>메시지마다 보내지 않는다.</b> 그 방의 관리자 미읽음이 0에서 1로 바뀌는 순간에만 보낸다 —
     * 안 그러면 사용자가 세 줄로 나눠 쓰면 알림이 세 개 온다. 관리자가 읽고 나면 다시 0이 되므로
     * 그다음 새 메시지에는 또 알림이 간다.
     */
    SUPPORT_MESSAGE_RECEIVED,

    /** 판매자가 주문의 배송 단계를 바꿈 — 그 주문의 구매자(007). */
    SHIPMENT_UPDATED
}
