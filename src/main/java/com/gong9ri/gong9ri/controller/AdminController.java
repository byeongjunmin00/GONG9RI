package com.gong9ri.gong9ri.controller;

import com.gong9ri.gong9ri.common.response.ApiResponse;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.dto.AdminDashboardResponse;
import com.gong9ri.gong9ri.dto.AdminMemberPageResponse;
import com.gong9ri.gong9ri.service.SupportChatService;
import com.gong9ri.gong9ri.dto.SupportRoomResponse;
import com.gong9ri.gong9ri.dto.ProductPageResponse;
import com.gong9ri.gong9ri.dto.AdminRefundPageResponse;
import com.gong9ri.gong9ri.entity.RefundRequestStatus;
import com.gong9ri.gong9ri.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
    private final SupportChatService supportChatService;

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
    @GetMapping("/products")
    public ResponseEntity<ApiResponse<ProductPageResponse>> products(
            @AuthenticationPrincipal MemberUserDetails principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(adminService.products(principal, page, size)));
    }

    /**
     * 상품 삭제. {@code force=true}면 결제·리뷰·공구팀까지 함께 지운다 — 장난성 게시물 정리용이고,
     * 되돌릴 수 없다. 되돌릴 수 있는 정리가 필요하면 숨김(PATCH .../hidden)을 쓴다.
     */
    @DeleteMapping("/products/{productId}")
    public ResponseEntity<Void> deleteProduct(
            @AuthenticationPrincipal MemberUserDetails principal,
            @PathVariable Long productId,
            @RequestParam(defaultValue = "false") boolean force) {
        adminService.deleteProduct(principal, productId, force);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/products/{productId}/hidden")
    public ResponseEntity<Void> setProductHidden(
            @AuthenticationPrincipal MemberUserDetails principal,
            @PathVariable Long productId,
            @RequestParam boolean hidden) {
        adminService.setProductHidden(principal, productId, hidden);
        return ResponseEntity.noContent().build();
    }

    // 관리자 상담 목록(support/chat) — 답을 기다리는 방이 위로 온다.
    @GetMapping("/support/rooms")
    public ResponseEntity<ApiResponse<Page<SupportRoomResponse>>> supportRooms(
            @AuthenticationPrincipal MemberUserDetails principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                supportChatService.roomsForAdmin(principal.getMember(), page, size)));
    }

    @GetMapping("/support/unread-count")
    public ResponseEntity<ApiResponse<Long>> supportUnreadCount(
            @AuthenticationPrincipal MemberUserDetails principal) {
        return ResponseEntity.ok(ApiResponse.success(
                supportChatService.unreadRoomCountForAdmin(principal.getMember())));
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
