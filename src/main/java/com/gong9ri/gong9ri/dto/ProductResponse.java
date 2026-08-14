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
        LocalDateTime createdAt,
        String imageUrl,
        // 참여 취소로 생긴 환불 요청을 판매자 승인 없이 즉시 처리하는지(상품 단위 설정) —
        // 판매자 등록/수정 폼 프리필에도 쓰인다.
        Boolean autoRefundOnCancel
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
                product.getCreatedAt(),
                product.getImageUrl(),
                product.isAutoRefundOnCancel()
        );
    }
}
