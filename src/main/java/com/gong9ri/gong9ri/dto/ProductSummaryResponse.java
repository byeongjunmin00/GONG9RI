package com.gong9ri.gong9ri.dto;

import com.gong9ri.gong9ri.entity.Product;
import com.gong9ri.gong9ri.entity.ProductCategory;
import java.time.LocalDateTime;

public record ProductSummaryResponse(
        Long productId,
        String name,
        Integer basePrice,
        Integer bestPrice,
        Integer maxParticipants,
        String sellerName,
        LocalDateTime createdAt,
        String imageUrl,
        ProductCategory category,
        // 메인 페이지 카드 진행바용(product/list-progress) — 이 상품의 RECRUITING 팀 중 진행률
        // (currentCount/targetParticipants)이 가장 높은 팀의 스냅샷. 진행 중인 팀이 하나도 없으면 둘 다
        // null(프론트는 이때 진행바를 숨긴다). 팀 상태는 참가/취소마다 바뀌는 값이라 PRODUCT_LIST_CACHE
        // 캐시 안에 포함시키면 최대 30분(TTL) 동안 낡은 값을 보여줄 수 있어, 이 필드는 캐시된
        // ProductService.list() 결과에 넣지 않고 항상 별도의 캐시 없는 조회(attachActiveTeamProgress)로
        // 채운다 — 판매자 수익현황을 Redis+TTL 캐싱에서 뺀 것과 같은 이유(staleness 회피).
        Integer activeTeamCurrentCount,
        Integer activeTeamTargetParticipants
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
                product.getImageUrl(),
                product.getCategory(),
                null,
                null
        );
    }

    public ProductSummaryResponse withActiveTeamProgress(Integer currentCount, Integer targetParticipants) {
        return new ProductSummaryResponse(productId, name, basePrice, bestPrice, maxParticipants, sellerName,
                createdAt, imageUrl, category, currentCount, targetParticipants);
    }
}
