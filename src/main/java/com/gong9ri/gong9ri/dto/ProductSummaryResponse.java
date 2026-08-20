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
        Integer activeTeamTargetParticipants,
        // 마감임박 표시용(product/list-sort) — activeTeamCurrentCount/TargetParticipants와 같은 팀(진행률
        // 최고 팀)의 마감일. 별도 팀을 다시 골라 뽑지 않고 이미 선택된 대표 팀의 값을 그대로 재사용한다
        // (마감임박순 *정렬*은 이것과 무관하게 별도로 가장 이른 마감일의 팀을 DB에서 직접 고른다 —
        // ProductRepositoryImpl 참고, 정렬 기준과 카드 표시 기준이 다를 수 있음은 POPULAR와 동일한 설계).
        LocalDateTime activeTeamDeadline,
        // 오픈예정(product/product-launch) — null이면 이미 공개된 상품. 미래 시각이면 카드에 "오픈예정"
        // 배지를 띄우고 혼자구매·신규 팀 신설 버튼을 비활성화한다(최종 판정은 서버, PRODUCT_NOT_YET_OPEN).
        LocalDateTime openAt,
        // 판매자 신뢰 배지(product/seller-trust) — 이 판매자의 전체 상품에 달린 리뷰 평균 평점·개수가
        // 기준(ProductService.TRUSTED_SELLER_*)을 넘으면 true. 새 평판 시스템을 따로 만들지 않고 이미
        // 있는 리뷰 데이터로만 계산한다.
        //
        // 반드시 boolean이 아니라 Boolean(boxed)이어야 한다 — 이 필드는 목록 캐시(PRODUCT_LIST_CACHE,
        // Redis+JSON, 30분 TTL)에 그대로 실려 저장된다. 이 필드를 추가하기 "전"에 이미 캐시돼 있던
        // 항목을 배포 직후 읽으면, 그 JSON엔 이 필드 자체가 없다 — primitive boolean이면 Jackson이
        // FAIL_ON_NULL_FOR_PRIMITIVES 때문에 MismatchedInputException을 던져 GET /api/products가
        // 그대로 500이 나버린다(실제로 프로덕션에서 재현됨, docs/logs 참고). Boolean이면 그냥 null로
        // 채워져 조용히 지나간다. openAt/activeTeamCurrentCount 등 이후에 추가된 캐시 필드가 전부
        // 참조형(Integer/LocalDateTime)인 것도 같은 이유 — 이 캐시에 새 필드를 추가할 땐 항상 boxed
        // 타입을 써야 한다.
        Boolean sellerTrustedBadge,
        Double ratingAverage,
        Integer reviewCount
) {
    public static ProductSummaryResponse of(Product product, Integer bestPrice, boolean sellerTrustedBadge) {
        return of(product, bestPrice, sellerTrustedBadge, null, null);
    }

    public static ProductSummaryResponse of(Product product, Integer bestPrice, boolean sellerTrustedBadge,
            Double ratingAverage, Integer reviewCount) {
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
                null,
                null,
                product.getOpenAt(),
                sellerTrustedBadge,
                ratingAverage,
                reviewCount
        );
    }

    public ProductSummaryResponse withActiveTeamProgress(Integer currentCount, Integer targetParticipants,
            LocalDateTime deadline) {
        return new ProductSummaryResponse(productId, name, basePrice, bestPrice, maxParticipants, sellerName,
                createdAt, imageUrl, category, currentCount, targetParticipants, deadline, openAt, sellerTrustedBadge,
                ratingAverage, reviewCount);
    }

    public ProductSummaryResponse withReviewStats(Double ratingAverage, Integer reviewCount) {
        return new ProductSummaryResponse(productId, name, basePrice, bestPrice, maxParticipants, sellerName,
                createdAt, imageUrl, category, activeTeamCurrentCount, activeTeamTargetParticipants, activeTeamDeadline,
                openAt, sellerTrustedBadge, ratingAverage, reviewCount);
    }
}
