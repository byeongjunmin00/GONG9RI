package com.gong9ri.gong9ri.dto;

import com.gong9ri.gong9ri.entity.Payment;
import com.gong9ri.gong9ri.entity.RefundRequest;
import com.gong9ri.gong9ri.entity.RefundRequestStatus;
import java.time.LocalDateTime;

public record RefundRequestResponse(
        Long refundRequestId,
        Long paymentId,
        Long productId,
        String productName,
        Long teamId,
        // 누가 요청했는지. 판매자·관리자 화면이 "누구의 환불 요청인지"를 알아야 처리할 수 있다
        // (기존엔 상품명만 내려가서 화면에 사람이 안 보였다, 2026-08-20). 구매자 본인 조회에서는
        // 자기 이름이 그대로 내려온다.
        Long requesterId,
        String requesterName,
        Integer amount,
        String paymentStatus,
        RefundRequestStatus status,
        String reason,
        // 거절 사유 템플릿의 설명 문구. REJECTED가 아니면 null.
        String rejectionReason,
        LocalDateTime requestedAt,
        LocalDateTime decidedAt,
        // 썸네일 표시용 대표 이미지 URL(null이면 프론트에서 기본 아이콘으로 대체).
        String imageUrl,
        // 프로필 사진(member/profile-image 노출, 2026-08-21). 요청자 이름과 같은 회원 엔티티에서 읽으므로
        // 추가 조회가 없다. 없으면 null → 프론트가 요청자 이름 첫 글자 동그라미를 그린다.
        String requesterProfileImageUrl
) {
    public static RefundRequestResponse from(RefundRequest refundRequest) {
        Payment payment = refundRequest.getPayment();
        return new RefundRequestResponse(
                refundRequest.getId(),
                payment.getId(),
                payment.getProduct().getId(),
                payment.getProduct().getName(),
                payment.getTeam() != null ? payment.getTeam().getId() : null,
                refundRequest.getRequester().getId(),
                refundRequest.getRequester().getName(),
                payment.getAmount(),
                payment.getStatus().name(),
                refundRequest.getStatus(),
                refundRequest.getReason(),
                refundRequest.getRejectionReason() != null ? refundRequest.getRejectionReason().getDescription() : null,
                refundRequest.getRequestedAt(),
                refundRequest.getDecidedAt(),
                payment.getProduct().getImageUrl(),
                refundRequest.getRequester().getProfileImageUrl()
        );
    }
}
