package com.gong9ri.gong9ri.controller;

import com.gong9ri.gong9ri.common.response.ApiResponse;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.dto.BuyerTeamResponse;
import com.gong9ri.gong9ri.dto.NotificationResponse;
import com.gong9ri.gong9ri.dto.PurchaseResponse;
import com.gong9ri.gong9ri.service.BuyerMypageService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> notifications(
            @AuthenticationPrincipal MemberUserDetails principal) {
        return ResponseEntity.ok(ApiResponse.success(buyerMypageService.notifications(principal)));
    }
}
