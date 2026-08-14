package com.gong9ri.gong9ri.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 솔로 구매(payment.team == null) 건에 대한 구매자 직접 환불 요청 — 사유 입력이 필수다(팀 결제의
 * 참여 취소 자동 요청과 달리 "환불이 필요한 이유"를 서버가 짐작할 수 없기 때문).
 */
public record RefundRequestCreateRequest(
        @NotBlank String reason
) {
}
