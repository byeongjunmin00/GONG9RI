package com.gong9ri.gong9ri.repository;

import static com.gong9ri.gong9ri.entity.QWishlist.wishlist;

import com.gong9ri.gong9ri.entity.Wishlist;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.util.List;

public class WishlistRepositoryImpl implements WishlistRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public WishlistRepositoryImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Override
    public List<Wishlist> findAllByMemberIdWithProduct(Long memberId) {
        return queryFactory
                .selectFrom(wishlist)
                .join(wishlist.product).fetchJoin()
                .join(wishlist.product.seller).fetchJoin()
                .where(wishlist.member.id.eq(memberId))
                .orderBy(wishlist.createdAt.desc())
                .fetch();
    }
}
