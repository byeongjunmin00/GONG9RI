package com.gong9ri.gong9ri.controller;

import com.gong9ri.gong9ri.common.response.ApiResponse;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.service.WishlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistService wishlistService;

    @PostMapping("/api/products/{productId}/wishlist")
    public ResponseEntity<ApiResponse<Void>> add(
            @AuthenticationPrincipal MemberUserDetails principal,
            @PathVariable Long productId) {
        wishlistService.add(principal, productId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
    }

    @DeleteMapping("/api/products/{productId}/wishlist")
    public ResponseEntity<Void> remove(
            @AuthenticationPrincipal MemberUserDetails principal,
            @PathVariable Long productId) {
        wishlistService.remove(principal, productId);
        return ResponseEntity.noContent().build();
    }
}
