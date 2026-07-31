package com.gong9ri.gong9ri.dto;

import com.gong9ri.gong9ri.entity.PriceTier;
import com.gong9ri.gong9ri.entity.Product;
import java.time.LocalDateTime;
import java.util.List;

public record ProductResponse(
        Long productId,
        Long sellerId,
        String sellerName,
        String name,
        String description,
        Integer basePrice,
        Integer maxParticipants,
        List<PriceTierResponse> priceTiers,
        LocalDateTime createdAt
) {
    public static ProductResponse of(Product product, List<PriceTier> priceTiers) {
        return new ProductResponse(
                product.getId(),
                product.getSeller().getId(),
                product.getSeller().getName(),
                product.getName(),
                product.getDescription(),
                product.getBasePrice(),
                product.getMaxParticipants(),
                priceTiers.stream().map(PriceTierResponse::from).toList(),
                product.getCreatedAt()
        );
    }
}
