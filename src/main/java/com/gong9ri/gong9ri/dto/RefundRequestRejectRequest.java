package com.gong9ri.gong9ri.dto;

import com.gong9ri.gong9ri.entity.RefundRejectionReason;
import jakarta.validation.constraints.NotNull;

/**
 * 판매자의 환불 요청 거절 — 자유 텍스트가 아니라 정해진 사유 템플릿({@code RefundRejectionReason}) 중
 * 하나를 고른다(사용자 확인 사항).
 */
public record RefundRequestRejectRequest(
        @NotNull RefundRejectionReason rejectionReason
) {
}
