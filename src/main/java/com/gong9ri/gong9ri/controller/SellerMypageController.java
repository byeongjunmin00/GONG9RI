package com.gong9ri.gong9ri.controller;

import com.gong9ri.gong9ri.common.response.ApiResponse;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.dto.NotificationListResponse;
import com.gong9ri.gong9ri.dto.RefundRequestResponse;
import com.gong9ri.gong9ri.dto.RevenueResponse;
import com.gong9ri.gong9ri.dto.SellerOrderResponse;
import com.gong9ri.gong9ri.dto.SellerProductResponse;
import com.gong9ri.gong9ri.dto.SellerTeamResponse;
import com.gong9ri.gong9ri.service.SellerMypageService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seller/mypage")
@RequiredArgsConstructor
public class SellerMypageController {

    private final SellerMypageService sellerMypageService;

    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<List<SellerOrderResponse>>> orders(
            @AuthenticationPrincipal MemberUserDetails principal) {
        return ResponseEntity.ok(ApiResponse.success(sellerMypageService.orders(principal)));
    }

    @GetMapping("/products")
    public ResponseEntity<ApiResponse<List<SellerProductResponse>>> products(
            @AuthenticationPrincipal MemberUserDetails principal) {
        return ResponseEntity.ok(ApiResponse.success(sellerMypageService.products(principal)));
    }

    @GetMapping("/revenue")
    public ResponseEntity<ApiResponse<RevenueResponse>> revenue(
            @AuthenticationPrincipal MemberUserDetails principal) {
        return ResponseEntity.ok(ApiResponse.success(sellerMypageService.revenue(principal)));
    }

    @GetMapping("/teams")
    public ResponseEntity<ApiResponse<List<SellerTeamResponse>>> teams(
            @AuthenticationPrincipal MemberUserDetails principal) {
        return ResponseEntity.ok(ApiResponse.success(sellerMypageService.teams(principal)));
    }

    @GetMapping("/notifications")
    public ResponseEntity<ApiResponse<NotificationListResponse>> notifications(
            @AuthenticationPrincipal MemberUserDetails principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(sellerMypageService.notifications(principal, page, size)));
    }

    @PostMapping("/notifications/{notificationId}/read")
    public ResponseEntity<ApiResponse<Void>> markNotificationAsRead(
            @AuthenticationPrincipal MemberUserDetails principal, @PathVariable Long notificationId) {
        sellerMypageService.markNotificationAsRead(principal, notificationId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/notifications/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllNotificationsAsRead(
            @AuthenticationPrincipal MemberUserDetails principal) {
        sellerMypageService.markAllNotificationsAsRead(principal);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/refund-requests")
    public ResponseEntity<ApiResponse<List<RefundRequestResponse>>> refundRequests(
            @AuthenticationPrincipal MemberUserDetails principal) {
        return ResponseEntity.ok(ApiResponse.success(sellerMypageService.refundRequests(principal)));
    }
}
