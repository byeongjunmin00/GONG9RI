package com.gong9ri.gong9ri.controller;

import com.gong9ri.gong9ri.common.response.ApiResponse;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.dto.ProductAiSuggestion;
import com.gong9ri.gong9ri.dto.ProductAiSuggestionRequest;
import com.gong9ri.gong9ri.service.AiProductSuggestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seller/products")
@RequiredArgsConstructor
public class ProductAiController {

    private final AiProductSuggestionService aiProductSuggestionService;

    @PostMapping("/ai-suggest")
    public ResponseEntity<ApiResponse<ProductAiSuggestion>> aiSuggest(
            @AuthenticationPrincipal MemberUserDetails principal,
            @Valid @RequestBody ProductAiSuggestionRequest request) {
        ProductAiSuggestion response = aiProductSuggestionService.suggest(principal, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
