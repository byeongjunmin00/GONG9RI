package com.gong9ri.gong9ri.repository;

import static com.gong9ri.gong9ri.entity.QPriceTier.priceTier;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;

public class PriceTierRepositoryImpl implements PriceTierRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public PriceTierRepositoryImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Override
    public List<BestPriceProjection> findBestPricesByProductIds(List<Long> productIds) {
        List<BestPriceProjectionImpl> rows = queryFactory
                .select(Projections.constructor(BestPriceProjectionImpl.class,
                        priceTier.product.id, priceTier.price.min()))
                .from(priceTier)
                .where(priceTier.product.id.in(productIds))
                .groupBy(priceTier.product.id)
                .fetch();
        return new ArrayList<>(rows);
    }
}
