package com.gong9ri.gong9ri.repository;

import static com.gong9ri.gong9ri.entity.QProduct.product;

import com.gong9ri.gong9ri.entity.Product;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public class ProductRepositoryImpl implements ProductRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public ProductRepositoryImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Override
    public Page<Product> findAllWithSeller(Pageable pageable) {
        List<Product> content = queryFactory
                .selectFrom(product)
                .join(product.seller).fetchJoin()
                .orderBy(toOrderSpecifiers(pageable.getSort()))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(product.count())
                .from(product)
                .fetchOne();

        return new PageImpl<>(content, pageable, Objects.requireNonNullElse(total, 0L));
    }

    @Override
    public Optional<Product> findByIdWithSeller(Long id) {
        Product result = queryFactory
                .selectFrom(product)
                .join(product.seller).fetchJoin()
                .where(product.id.eq(id))
                .fetchOne();
        return Optional.ofNullable(result);
    }

    // pageable에 Sort가 없으면(현재 호출부는 항상 정렬 없이 호출) ORDER BY 없이 원래 JPQL과 동일하게 동작한다.
    private OrderSpecifier<?>[] toOrderSpecifiers(Sort sort) {
        if (sort.isUnsorted()) {
            return new OrderSpecifier<?>[0];
        }
        PathBuilder<Product> pathBuilder = new PathBuilder<>(Product.class, "product");
        List<OrderSpecifier<?>> orders = new ArrayList<>();
        for (Sort.Order order : sort) {
            Order direction = order.isAscending() ? Order.ASC : Order.DESC;
            PathBuilder<Comparable> path = pathBuilder.get(order.getProperty(), Comparable.class);
            orders.add(new OrderSpecifier<>(direction, path));
        }
        return orders.toArray(new OrderSpecifier<?>[0]);
    }
}
