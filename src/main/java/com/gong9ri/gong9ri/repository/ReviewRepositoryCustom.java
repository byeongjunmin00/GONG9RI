package com.gong9ri.gong9ri.repository;

import java.util.List;

public interface ReviewRepositoryCustom {

    // 판매자 신뢰 배지용(product/seller-trust) — 해당 판매자들의 상품 전체에 달린 리뷰를 평균 평점·
    // 개수로 집계한다(product_id → seller_id 조인, 리뷰가 하나도 없는 판매자는 결과에 아예 안 나온다).
    List<SellerRatingProjection> findSellerRatingSummaries(List<Long> sellerIds);

    // 상품 카드/상세용 리뷰 별점 & 리뷰수 집계
    List<ProductReviewStatProjection> findProductReviewStats(List<Long> productIds);
}
