package com.gong9ri.gong9ri.dto;

import com.gong9ri.gong9ri.entity.Product;
import java.time.LocalDateTime;

public record SellerProductResponse(
        Long productId,
        String name,
        Integer basePrice,
        Integer maxParticipants,
        LocalDateTime createdAt,
        // 썸네일 표시용 대표 이미지 URL(null이면 프론트에서 기본 아이콘으로 대체).
        String imageUrl
) {
    public static SellerProductResponse from(Product product) {
        return new SellerProductResponse(
                product.getId(),
                product.getName(),
                product.getBasePrice(),
                product.getMaxParticipants(),
                product.getCreatedAt(),
                product.getImageUrl()
        );
    }
}
