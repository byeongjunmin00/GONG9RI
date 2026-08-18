package com.gong9ri.gong9ri.dto;

import com.gong9ri.gong9ri.entity.PriceTier;
import com.gong9ri.gong9ri.entity.Product;
import com.gong9ri.gong9ri.entity.ProductCategory;
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
        String kakaoJsKey,
        // 메인 페이지 카테고리 필터용(product/category). 판매자 등록/수정 폼 프리필에도 쓰인다.
        ProductCategory category,
        // 오픈예정(product/product-launch) — null이면 이미 공개된 상품(하위 호환 기본값). 미래 시각이면
        // 그 전까지 혼자구매·신규 팀 신설이 서버에서 거절된다(PRODUCT_NOT_YET_OPEN). 판매자 등록/수정
        // 폼 프리필에도 쓰인다.
        LocalDateTime openAt,
        // 판매자 신뢰 배지(product/seller-trust) — ProductSummaryResponse와 동일 기준. boolean이 아니라
        // Boolean이어야 하는 이유도 동일(ProductSummaryResponse.sellerTrustedBadge 필드 주석 참고 —
        // 이 필드 추가 이전에 캐시된 상세 응답을 배포 직후 읽으면 primitive는 역직렬화 시 500을 낸다).
        Boolean sellerTrustedBadge
) {
    public static ProductResponse of(Product product, List<PriceTier> priceTiers, String kakaoJsKey,
            boolean sellerTrustedBadge) {
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
                kakaoJsKey,
                product.getCategory(),
                product.getOpenAt(),
                sellerTrustedBadge
        );
    }
}
