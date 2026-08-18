package com.gong9ri.gong9ri.controller;

import com.gong9ri.gong9ri.common.response.ApiResponse;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.dto.InquiryAnswerRequest;
import com.gong9ri.gong9ri.dto.InquiryCreateRequest;
import com.gong9ri.gong9ri.dto.InquiryListResponse;
import com.gong9ri.gong9ri.dto.InquiryResponse;
import com.gong9ri.gong9ri.service.InquiryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class InquiryController {

    private final InquiryService inquiryService;

    @GetMapping("/api/products/{productId}/inquiries")
    public ResponseEntity<ApiResponse<InquiryListResponse>> list(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.success(inquiryService.list(productId)));
    }

    @PostMapping("/api/products/{productId}/inquiries")
    public ResponseEntity<ApiResponse<InquiryResponse>> create(
            @AuthenticationPrincipal MemberUserDetails principal,
            @PathVariable Long productId,
            @Valid @RequestBody InquiryCreateRequest request) {
        InquiryResponse response = inquiryService.create(principal, productId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PutMapping("/api/inquiries/{inquiryId}")
    public ResponseEntity<ApiResponse<InquiryResponse>> update(
            @AuthenticationPrincipal MemberUserDetails principal,
            @PathVariable Long inquiryId,
            @Valid @RequestBody InquiryCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(inquiryService.update(principal, inquiryId, request)));
    }

    @DeleteMapping("/api/inquiries/{inquiryId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal MemberUserDetails principal,
            @PathVariable Long inquiryId) {
        inquiryService.delete(principal, inquiryId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/inquiries/{inquiryId}/answer")
    public ResponseEntity<ApiResponse<InquiryResponse>> registerAnswer(
            @AuthenticationPrincipal MemberUserDetails principal,
            @PathVariable Long inquiryId,
            @Valid @RequestBody InquiryAnswerRequest request) {
        InquiryResponse response = inquiryService.registerAnswer(principal, inquiryId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PutMapping("/api/inquiries/{inquiryId}/answer")
    public ResponseEntity<ApiResponse<InquiryResponse>> updateAnswer(
            @AuthenticationPrincipal MemberUserDetails principal,
            @PathVariable Long inquiryId,
            @Valid @RequestBody InquiryAnswerRequest request) {
        return ResponseEntity.ok(ApiResponse.success(inquiryService.updateAnswer(principal, inquiryId, request)));
    }

    @DeleteMapping("/api/inquiries/{inquiryId}/answer")
    public ResponseEntity<Void> deleteAnswer(
            @AuthenticationPrincipal MemberUserDetails principal,
            @PathVariable Long inquiryId) {
        inquiryService.deleteAnswer(principal, inquiryId);
        return ResponseEntity.noContent().build();
    }
}
