package com.gong9ri.gong9ri.dto;

import com.gong9ri.gong9ri.entity.Payment;
import com.gong9ri.gong9ri.entity.ShipmentStatus;
import java.time.LocalDateTime;

public record PurchaseResponse(
        Long paymentId,
        Long productId,
        String productName,
        Integer amount,
        String status,
        LocalDateTime paidAt,
        // 팀이 딸린 결제면 teamId, 혼자구매면 null — 프론트가 "직접 환불 요청" 버튼을 솔로 구매 건에만
        // 노출하는 데 쓴다(팀 결제의 환불은 오직 참여 취소로만 가능, docs/api/refund.md).
        Long teamId,
        // 공구팀 번호(admin-identifier-codes, 2026-08-22) — teamId와 같은 자리에 노출한다. 혼자구매면
        // teamId와 마찬가지로 null.
        String teamNo,
        // 썸네일 표시용 대표 이미지 URL(null이면 프론트에서 기본 아이콘으로 대체).
        String imageUrl,
        // 판매자가 조작하는 배송 단계(007) — 읽기 전용. 판매자 쪽 SellerOrderResponse와 동일한 필드,
        // 여긴 구매자용이라 변경 API는 없고 표시만 한다.
        ShipmentStatus shipmentStatus,
        String shipmentStatusLabel,
        String trackingCarrier,
        String trackingNumber
) {
    public static PurchaseResponse from(Payment payment) {
        return new PurchaseResponse(
                payment.getId(),
                payment.getProduct().getId(),
                payment.getProduct().getName(),
                payment.getAmount(),
                payment.getStatus().name(),
                payment.getPaidAt(),
                payment.getTeam() != null ? payment.getTeam().getId() : null,
                payment.getTeam() != null ? payment.getTeam().getTeamNo() : null,
                payment.getProduct().getImageUrl(),
                payment.getShipmentStatus(),
                payment.getShipmentStatus().label(),
                payment.getTrackingCarrier(),
                payment.getTrackingNumber()
        );
    }
}
