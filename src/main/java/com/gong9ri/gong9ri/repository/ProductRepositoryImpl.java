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
import java.time.LocalDateTime;
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
            String keyword, boolean openSoon) {
        BooleanExpression categoryCondition = category == null ? null : product.category.eq(category);
        // 검색어(product/list-search) — 상품명 또는 판매자명에 포함되면 매치(대소문자 무시). 둘 중
        // 하나만 걸려도 되는 OR 조건이라 category(AND)와 별도 변수로 둔다.
        BooleanExpression keywordCondition = (keyword == null || keyword.isBlank())
                ? null
                : product.name.containsIgnoreCase(keyword).or(product.seller.name.containsIgnoreCase(keyword));

        // 오픈예정 탭 필터(product/list-enhancements) — openSoon=true면 아직 공개 전인(openAt이 미래인)
        // 상품만, 아니면서 category가 지정된 경우(특정 카테고리 탭)는 반대로 아직 공개 전인 상품을
        // 제외한다(그 카테고리 탭에는 오픈예정 상품이 보이지 않아야 한다). category도 openSoon도 없으면
        // (전체 탭, 카테고리 미지정 검색) 이 조건은 아예 걸지 않아 기존과 동일하게 전부 포함한다.
        LocalDateTime now = LocalDateTime.now();
        BooleanExpression openSoonCondition;
        if (openSoon) {
            openSoonCondition = product.openAt.isNotNull().and(product.openAt.after(now));
        } else if (category != null) {
            openSoonCondition = product.openAt.isNull().or(product.openAt.loe(now));
        } else {
            openSoonCondition = null;
        }

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
        } else if (sort == ProductSort.DEADLINE) {
            // 마감임박순(product/list-sort) — 이 상품의 RECRUITING 팀 중 가장 이른 마감일 기준 오름차순.
            // MIN() 상관 서브쿼리도 POPULAR와 동일한 이유(페이지네이션 경계)로 DB 레벨에서 정렬한다.
            // MySQL은 ASC 정렬에서 NULL을 맨 앞으로 보내는데(POPULAR의 DESC와 반대), 그러면 진행 중인
            // 팀이 하나도 없는 상품이 "제일 급한" 것처럼 맨 위로 온다 — 의도와 반대라 nullsLast()로
            // 명시적으로 뒤로 보낸다.
            Expression<LocalDateTime> nearestDeadline = JPAExpressions
                    .select(groupBuyTeam.deadline.min())
                    .from(groupBuyTeam)
                    .where(groupBuyTeam.product.eq(product).and(groupBuyTeam.status.eq(TeamStatus.RECRUITING)));
            orders.add(new OrderSpecifier<>(Order.ASC, nearestDeadline).nullsLast());
        }
        orders.addAll(List.of(toOrderSpecifiers(pageable.getSort())));

        List<Product> content = queryFactory
                .selectFrom(product)
                .join(product.seller).fetchJoin()
                .where(categoryCondition, keywordCondition, openSoonCondition)
                .orderBy(orders.toArray(new OrderSpecifier<?>[0]))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(product.count())
                .from(product)
                .join(product.seller)
                .where(categoryCondition, keywordCondition, openSoonCondition)
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
