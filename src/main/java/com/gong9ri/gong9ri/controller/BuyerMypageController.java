package com.gong9ri.gong9ri.controller;

import com.gong9ri.gong9ri.common.response.ApiResponse;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.dto.BuyerTeamResponse;
import com.gong9ri.gong9ri.dto.NotificationListResponse;
import com.gong9ri.gong9ri.dto.PurchaseResponse;
import com.gong9ri.gong9ri.dto.RefundRequestResponse;
import com.gong9ri.gong9ri.dto.WishlistItemResponse;
import com.gong9ri.gong9ri.service.BuyerMypageService;
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
@RequestMapping("/api/buyer/mypage")
@RequiredArgsConstructor
public class BuyerMypageController {

    private final BuyerMypageService buyerMypageService;

    @GetMapping("/purchases")
    public ResponseEntity<ApiResponse<List<PurchaseResponse>>> purchases(
            @AuthenticationPrincipal MemberUserDetails principal) {
        return ResponseEntity.ok(ApiResponse.success(buyerMypageService.purchases(principal)));
    }

    @GetMapping("/teams")
    public ResponseEntity<ApiResponse<List<BuyerTeamResponse>>> teams(
            @AuthenticationPrincipal MemberUserDetails principal) {
        return ResponseEntity.ok(ApiResponse.success(buyerMypageService.teams(principal)));
    }

    @GetMapping("/notifications")
    public ResponseEntity<ApiResponse<NotificationListResponse>> notifications(
            @AuthenticationPrincipal MemberUserDetails principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(buyerMypageService.notifications(principal, page, size)));
    }

    @PostMapping("/notifications/{notificationId}/read")
    public ResponseEntity<ApiResponse<Void>> markNotificationAsRead(
            @AuthenticationPrincipal MemberUserDetails principal, @PathVariable Long notificationId) {
        buyerMypageService.markNotificationAsRead(principal, notificationId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/notifications/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllNotificationsAsRead(
            @AuthenticationPrincipal MemberUserDetails principal) {
        buyerMypageService.markAllNotificationsAsRead(principal);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/refund-requests")
    public ResponseEntity<ApiResponse<List<RefundRequestResponse>>> refundRequests(
            @AuthenticationPrincipal MemberUserDetails principal) {
        return ResponseEntity.ok(ApiResponse.success(buyerMypageService.refundRequests(principal)));
    }

    @GetMapping("/wishlist")
    public ResponseEntity<ApiResponse<List<WishlistItemResponse>>> wishlist(
            @AuthenticationPrincipal MemberUserDetails principal) {
        return ResponseEntity.ok(ApiResponse.success(buyerMypageService.wishlist(principal)));
    }
}
