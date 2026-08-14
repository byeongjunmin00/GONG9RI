package com.gong9ri.gong9ri.controller;

import com.gong9ri.gong9ri.common.response.ApiResponse;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.dto.RefundRequestCreateRequest;
import com.gong9ri.gong9ri.dto.RefundRequestRejectRequest;
import com.gong9ri.gong9ri.dto.RefundRequestResponse;
import com.gong9ri.gong9ri.service.RefundRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RefundRequestController {

    private final RefundRequestService refundRequestService;

    // 솔로 구매(payment.team == null) 건에 대한 구매자 직접 환불 요청. 팀이 딸린 결제는
    // TEAM_PAYMENT_REFUND_NOT_ALLOWED(409)로 거절된다(docs/api/refund.md).
    @PostMapping("/api/payments/{paymentId}/refund-requests")
    public ResponseEntity<ApiResponse<RefundRequestResponse>> create(
            @AuthenticationPrincipal MemberUserDetails principal,
            @PathVariable Long paymentId,
            @Valid @RequestBody RefundRequestCreateRequest request) {
        RefundRequestResponse response = refundRequestService.createDirect(principal, paymentId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping("/api/refund-requests/{refundRequestId}/approve")
    public ResponseEntity<ApiResponse<RefundRequestResponse>> approve(
            @AuthenticationPrincipal MemberUserDetails principal,
            @PathVariable Long refundRequestId) {
        return ResponseEntity.ok(ApiResponse.success(refundRequestService.approve(principal, refundRequestId)));
    }

    @PostMapping("/api/refund-requests/{refundRequestId}/reject")
    public ResponseEntity<ApiResponse<RefundRequestResponse>> reject(
            @AuthenticationPrincipal MemberUserDetails principal,
            @PathVariable Long refundRequestId,
            @Valid @RequestBody RefundRequestRejectRequest request) {
        return ResponseEntity.ok(ApiResponse.success(refundRequestService.reject(principal, refundRequestId, request)));
    }
}
