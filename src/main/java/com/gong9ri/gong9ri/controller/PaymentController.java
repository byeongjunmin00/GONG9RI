package com.gong9ri.gong9ri.controller;

import com.gong9ri.gong9ri.common.response.ApiResponse;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.dto.PaymentCreateRequest;
import com.gong9ri.gong9ri.dto.PaymentResponse;
import com.gong9ri.gong9ri.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<ApiResponse<PaymentResponse>> create(
            @AuthenticationPrincipal MemberUserDetails principal,
            @Valid @RequestBody PaymentCreateRequest request) {
        PaymentResponse response = paymentService.create(principal, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> detail(
            @AuthenticationPrincipal MemberUserDetails principal,
            @PathVariable Long paymentId) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.detail(principal, paymentId)));
    }
}
