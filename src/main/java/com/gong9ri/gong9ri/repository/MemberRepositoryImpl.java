package com.gong9ri.gong9ri.repository;

import static com.gong9ri.gong9ri.entity.QMember.member;

import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Role;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.util.StringUtils;

public class MemberRepositoryImpl implements MemberRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public MemberRepositoryImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Override
    public Page<Member> findAllForAdmin(Pageable pageable, String search, Role role, Boolean suspended) {
        BooleanExpression condition = searchCondition(search)
                .and(roleEq(role))
                .and(suspendedEq(suspended));

        List<Member> content = queryFactory
                .selectFrom(member)
                .where(condition)
                .orderBy(member.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(member.count())
                .from(member)
                .where(condition)
                .fetchOne();

        long totalCount = total != null ? total : 0L;
        return new PageImpl<>(content, pageable, totalCount);
    }

    private BooleanExpression searchCondition(String search) {
        if (!StringUtils.hasText(search)) {
            return member.id.isNotNull();
        }
        String keyword = search.trim();
        return member.username.containsIgnoreCase(keyword)
                .or(member.name.containsIgnoreCase(keyword))
                .or(member.email.containsIgnoreCase(keyword));
    }

    private BooleanExpression roleEq(Role role) {
        return role != null ? member.role.eq(role) : null;
    }

    private BooleanExpression suspendedEq(Boolean suspended) {
        return suspended != null ? member.suspended.eq(suspended) : null;
    }
}
