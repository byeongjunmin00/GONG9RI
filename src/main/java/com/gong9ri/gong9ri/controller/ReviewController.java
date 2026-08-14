package com.gong9ri.gong9ri.controller;

import com.gong9ri.gong9ri.common.response.ApiResponse;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.dto.ReviewCreateRequest;
import com.gong9ri.gong9ri.dto.ReviewListResponse;
import com.gong9ri.gong9ri.dto.ReviewResponse;
import com.gong9ri.gong9ri.service.ReviewService;
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
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping("/api/products/{productId}/reviews")
    public ResponseEntity<ApiResponse<ReviewListResponse>> list(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.success(reviewService.list(productId)));
    }

    @PostMapping("/api/products/{productId}/reviews")
    public ResponseEntity<ApiResponse<ReviewResponse>> create(
            @AuthenticationPrincipal MemberUserDetails principal,
            @PathVariable Long productId,
            @Valid @RequestBody ReviewCreateRequest request) {
        ReviewResponse response = reviewService.create(principal, productId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PutMapping("/api/reviews/{reviewId}")
    public ResponseEntity<ApiResponse<ReviewResponse>> update(
            @AuthenticationPrincipal MemberUserDetails principal,
            @PathVariable Long reviewId,
            @Valid @RequestBody ReviewCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(reviewService.update(principal, reviewId, request)));
    }

    @DeleteMapping("/api/reviews/{reviewId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal MemberUserDetails principal,
            @PathVariable Long reviewId) {
        reviewService.delete(principal, reviewId);
        return ResponseEntity.noContent().build();
    }
}
