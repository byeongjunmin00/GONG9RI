package com.gong9ri.gong9ri.dto;

import com.gong9ri.gong9ri.entity.Product;

public record ProductSearchResult(
        Long productId,
        String name,
        Integer basePrice,
        Integer maxParticipants
) {
    public static ProductSearchResult from(Product product) {
        return new ProductSearchResult(
                product.getId(),
                product.getName(),
                product.getBasePrice(),
                product.getMaxParticipants()
        );
    }
}
