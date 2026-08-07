package com.gong9ri.gong9ri.repository;

import java.util.List;

/**
 * PriceTierRepository의 QueryDSL 기반 커스텀 쿼리(그룹핑 집계).
 * 구현은 {@link PriceTierRepositoryImpl} 참고 — docs/dev/ongoing/querydsl-migration.md.
 */
public interface PriceTierRepositoryCustom {

    List<BestPriceProjection> findBestPricesByProductIds(List<Long> productIds);
}
