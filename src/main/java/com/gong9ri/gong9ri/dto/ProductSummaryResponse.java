package com.gong9ri.gong9ri.dto;

import com.gong9ri.gong9ri.entity.Product;
import java.time.LocalDateTime;

public record ProductSummaryResponse(
        Long productId,
        String name,
        Integer basePrice,
        Integer bestPrice,
        Integer maxParticipants,
        String sellerName,
        LocalDateTime createdAt,
        String imageUrl
) {
    public static ProductSummaryResponse of(Product product, Integer bestPrice) {
        return new ProductSummaryResponse(
                product.getId(),
                product.getName(),
                product.getBasePrice(),
                bestPrice,
                product.getMaxParticipants(),
                product.getSeller().getName(),
                product.getCreatedAt(),
                product.getImageUrl()
        );
    }
}
