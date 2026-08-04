package com.gong9ri.gong9ri.dto;

import com.gong9ri.gong9ri.entity.Product;
import java.time.LocalDateTime;

public record SellerProductResponse(
        Long productId,
        String name,
        Integer basePrice,
        Integer maxParticipants,
        LocalDateTime createdAt
) {
    public static SellerProductResponse from(Product product) {
        return new SellerProductResponse(
                product.getId(),
                product.getName(),
                product.getBasePrice(),
                product.getMaxParticipants(),
                product.getCreatedAt()
        );
    }
}
