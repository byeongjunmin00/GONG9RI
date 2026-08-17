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
        Boolean autoRefundOnCancel,
        // 카카오톡 공유하기(docs/dev/share/kakao-share/design.md)용 — 브라우저에서 Kakao.init(...)에
        // 그대로 쓰인다. portoneStoreId/portoneChannelKey를 PaymentResponse에 실어 내려주는 것과 동일한
        // 패턴(도메인 화이트리스트로 보호되는 공개 가능한 값이라 별도 인증 없이 내려줘도 무방).
        String kakaoJsKey
) {
    public static ProductResponse of(Product product, List<PriceTier> priceTiers, String kakaoJsKey) {
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
                product.isAutoRefundOnCancel(),
                kakaoJsKey
        );
    }
}
