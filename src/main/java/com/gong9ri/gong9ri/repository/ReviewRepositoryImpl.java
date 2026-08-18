package com.gong9ri.gong9ri.repository;

import static com.gong9ri.gong9ri.entity.QReview.review;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;

public class ReviewRepositoryImpl implements ReviewRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public ReviewRepositoryImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Override
    public List<SellerRatingProjection> findSellerRatingSummaries(List<Long> sellerIds) {
        List<SellerRatingProjectionImpl> rows = queryFactory
                .select(Projections.constructor(SellerRatingProjectionImpl.class,
                        review.product.seller.id, review.rating.avg(), review.count()))
                .from(review)
                .where(review.product.seller.id.in(sellerIds))
                .groupBy(review.product.seller.id)
                .fetch();
        return new ArrayList<>(rows);
    }
}
