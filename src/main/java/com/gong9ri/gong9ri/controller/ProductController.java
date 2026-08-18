package com.gong9ri.gong9ri.controller;

import com.gong9ri.gong9ri.common.response.ApiResponse;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.dto.ProductPageResponse;
import com.gong9ri.gong9ri.dto.ProductRegisterRequest;
import com.gong9ri.gong9ri.dto.ProductResponse;
import com.gong9ri.gong9ri.dto.ProductSort;
import com.gong9ri.gong9ri.entity.ProductCategory;
import com.gong9ri.gong9ri.service.ProductService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<ApiResponse<ProductPageResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) ProductCategory category,
            @RequestParam(required = false) ProductSort sort) {
        ProductPageResponse cached = productService.list(page, size, category, sort);
        ProductPageResponse withProgress = productService.attachActiveTeamProgress(cached);
        return ResponseEntity.ok(ApiResponse.success(withProgress));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductResponse>> detail(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.success(productService.detail(productId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> register(
            @AuthenticationPrincipal MemberUserDetails principal,
            @Valid @RequestBody ProductRegisterRequest request) {
        ProductResponse response = productService.register(principal, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PutMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductResponse>> update(
            @AuthenticationPrincipal MemberUserDetails principal,
            @PathVariable Long productId,
            @Valid @RequestBody ProductRegisterRequest request) {
        ProductResponse response = productService.update(principal, productId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal MemberUserDetails principal,
            @PathVariable Long productId) {
        productService.delete(principal, productId);
        return ResponseEntity.noContent().build();
    }
}
