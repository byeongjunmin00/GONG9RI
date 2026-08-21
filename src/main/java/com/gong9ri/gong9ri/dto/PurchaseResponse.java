package com.gong9ri.gong9ri.dto;

import com.gong9ri.gong9ri.entity.Payment;
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
        // 썸네일 표시용 대표 이미지 URL(null이면 프론트에서 기본 아이콘으로 대체).
        String imageUrl
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
                payment.getProduct().getImageUrl()
        );
    }
}
