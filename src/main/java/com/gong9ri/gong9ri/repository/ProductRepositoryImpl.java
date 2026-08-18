package com.gong9ri.gong9ri.repository;

import static com.gong9ri.gong9ri.entity.QGroupBuyTeam.groupBuyTeam;
import static com.gong9ri.gong9ri.entity.QProduct.product;

import com.gong9ri.gong9ri.dto.ProductSort;
import com.gong9ri.gong9ri.entity.Product;
import com.gong9ri.gong9ri.entity.ProductCategory;
import com.gong9ri.gong9ri.entity.TeamStatus;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.JPAExpressions;
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
    public Page<Product> findAllWithSeller(Pageable pageable, ProductCategory category, ProductSort sort,
            String keyword) {
        BooleanExpression categoryCondition = category == null ? null : product.category.eq(category);
        // 검색어(product/list-search) — 상품명 또는 판매자명에 포함되면 매치(대소문자 무시). 둘 중
        // 하나만 걸려도 되는 OR 조건이라 category(AND)와 별도 변수로 둔다.
        BooleanExpression keywordCondition = (keyword == null || keyword.isBlank())
                ? null
                : product.name.containsIgnoreCase(keyword).or(product.seller.name.containsIgnoreCase(keyword));

        List<OrderSpecifier<?>> orders = new ArrayList<>();
        if (sort == ProductSort.LATEST) {
            orders.add(product.createdAt.desc());
        } else if (sort == ProductSort.POPULAR) {
            // 인기순(product/list-sort) — 이 상품의 RECRUITING 팀 중 참여 인원이 가장 많은 팀의 인원수로
            // 정렬한다. 진행바(activeTeamCurrentCount)가 보여주는 "달성률이 가장 높은 팀"과는 다른
            // 선택 기준이다 — 인기순은 "얼마나 많이 모였는지" 자체가 신호라 순수 인원수(MAX)를 쓴다.
            // 상관 서브쿼리라 페이지네이션 이전(DB 레벨) 정렬이 가능하다 — 조회 후 자바에서 정렬하면
            // 페이지 경계가 어긋난다. RECRUITING 팀이 없는 상품은 서브쿼리가 NULL을 반환하고, MySQL은
            // DESC 정렬에서 NULL을 마지막으로 보내 자연스럽게 맨 뒤로 밀린다.
            Expression<Integer> popularTeamCount = JPAExpressions
                    .select(groupBuyTeam.currentCount.max())
                    .from(groupBuyTeam)
                    .where(groupBuyTeam.product.eq(product).and(groupBuyTeam.status.eq(TeamStatus.RECRUITING)));
            orders.add(new OrderSpecifier<>(Order.DESC, popularTeamCount));
        }
        orders.addAll(List.of(toOrderSpecifiers(pageable.getSort())));

        List<Product> content = queryFactory
                .selectFrom(product)
                .join(product.seller).fetchJoin()
                .where(categoryCondition, keywordCondition)
                .orderBy(orders.toArray(new OrderSpecifier<?>[0]))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(product.count())
                .from(product)
                .join(product.seller)
                .where(categoryCondition, keywordCondition)
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
