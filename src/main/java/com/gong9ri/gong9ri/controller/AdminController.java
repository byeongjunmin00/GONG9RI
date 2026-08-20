package com.gong9ri.gong9ri.controller;

import com.gong9ri.gong9ri.common.response.ApiResponse;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.dto.AdminDashboardResponse;
import com.gong9ri.gong9ri.dto.AdminMemberPageResponse;
import com.gong9ri.gong9ri.dto.AdminRefundPageResponse;
import com.gong9ri.gong9ri.entity.RefundRequestStatus;
import com.gong9ri.gong9ri.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<AdminDashboardResponse>> dashboard(
            @AuthenticationPrincipal MemberUserDetails principal) {
        return ResponseEntity.ok(ApiResponse.success(adminService.dashboard(principal)));
    }

    @GetMapping("/members")
    public ResponseEntity<ApiResponse<AdminMemberPageResponse>> members(
            @AuthenticationPrincipal MemberUserDetails principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(adminService.members(principal, page, size)));
    }

    @PostMapping("/members/{memberId}/suspend")
    public ResponseEntity<Void> suspendMember(
            @AuthenticationPrincipal MemberUserDetails principal,
            @PathVariable Long memberId) {
        adminService.suspendMember(principal, memberId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/members/{memberId}/unsuspend")
    public ResponseEntity<Void> unsuspendMember(
            @AuthenticationPrincipal MemberUserDetails principal,
            @PathVariable Long memberId) {
        adminService.unsuspendMember(principal, memberId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/members/{memberId}")
    public ResponseEntity<Void> deleteMember(
            @AuthenticationPrincipal MemberUserDetails principal,
            @PathVariable Long memberId) {
        adminService.deleteMember(principal, memberId);
        return ResponseEntity.noContent().build();
    }

    // 관리자 상품 삭제(product/admin) — 목록 조회는 공개 API(GET /api/products)를 그대로 쓰지만,
    // 삭제는 남의 상품을 지우는 것이라 관리자 전용 경로로 둔다.
    @DeleteMapping("/products/{productId}")
    public ResponseEntity<Void> deleteProduct(
            @AuthenticationPrincipal MemberUserDetails principal,
            @PathVariable Long productId) {
        adminService.deleteProduct(principal, productId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/refund-requests")
    public ResponseEntity<ApiResponse<AdminRefundPageResponse>> refundRequests(
            @AuthenticationPrincipal MemberUserDetails principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) RefundRequestStatus status) {
        return ResponseEntity.ok(ApiResponse.success(adminService.refundRequests(principal, page, size, status)));
    }
}
