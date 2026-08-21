package com.gong9ri.gong9ri.entity;

/**
 * 판매자가 직접 조작하는 배송 진행 단계 (docs/dev/mypage/view/changes/007-*.md).
 *
 * <p>결제 상태·공구팀 상태로부터 매 조회 때 계산되는 파생값(주문이 배송 대상인지/환불됐는지 등,
 * {@code SellerOrderResponse.derivePreparationStatus})과는 별개다 — 이건 DB에 저장되는 값이고,
 * 판매자가 언제든 자유롭게 4단계 중 아무거나로 바꿀 수 있다(순서 강제 없음, 2026-08-21 사용자 요구).
 */
public enum ShipmentStatus {
    PRODUCT_PREPARING("상품 준비중"),
    SHIPPING_PREPARING("배송 준비중"),
    IN_TRANSIT("배송중"),
    DELIVERED("배송완료");

    private final String label;

    ShipmentStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
