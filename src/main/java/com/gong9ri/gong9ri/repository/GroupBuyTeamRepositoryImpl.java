package com.gong9ri.gong9ri.repository;

import static com.gong9ri.gong9ri.entity.QGroupBuyTeam.groupBuyTeam;

import com.gong9ri.gong9ri.entity.GroupBuyTeam;
import com.gong9ri.gong9ri.entity.TeamStatus;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.DateTimeExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class GroupBuyTeamRepositoryImpl implements GroupBuyTeamRepositoryCustom {

    private final EntityManager entityManager;
    private final JPAQueryFactory queryFactory;

    public GroupBuyTeamRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Override
    public Optional<GroupBuyTeam> findByIdForUpdate(Long id) {
        GroupBuyTeam result = queryFactory
                .selectFrom(groupBuyTeam)
                .where(groupBuyTeam.id.eq(id))
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .fetchOne();
        return Optional.ofNullable(result);
    }

    @Override
    public List<GroupBuyTeam> findAllBySellerIdWithProduct(Long sellerId) {
        return queryFactory
                .selectFrom(groupBuyTeam)
                .join(groupBuyTeam.product).fetchJoin()
                // 리더 이름을 응답에 싣기 때문에 함께 가져온다 — LAZY라 안 붙이면 팀마다 한 번씩 더 나간다.
                .join(groupBuyTeam.leader).fetchJoin()
                .where(groupBuyTeam.product.seller.id.eq(sellerId))
                .orderBy(groupBuyTeam.createdAt.desc())
                .fetch();
    }

    @Override
    public List<Long> findIdsByStatusAndDeadlineBefore(TeamStatus status, LocalDateTime now) {
        return queryFactory
                .select(groupBuyTeam.id)
                .from(groupBuyTeam)
                .where(groupBuyTeam.status.eq(status), groupBuyTeam.deadline.before(now))
                .fetch();
    }

    @Override
    public int incrementIfCapacity(Long id) {
        long updated = queryFactory
                .update(groupBuyTeam)
                .set(groupBuyTeam.currentCount, groupBuyTeam.currentCount.add(1))
                .set(groupBuyTeam.status, new CaseBuilder()
                        .when(groupBuyTeam.currentCount.add(1).eq(groupBuyTeam.maxParticipants))
                        .then(TeamStatus.SUCCESS)
                        .otherwise(groupBuyTeam.status))
                .set(groupBuyTeam.updatedAt, DateTimeExpression.currentTimestamp(LocalDateTime.class))
                .where(groupBuyTeam.id.eq(id), groupBuyTeam.currentCount.lt(groupBuyTeam.maxParticipants))
                .execute();
        // 원래 @Modifying(clearAutomatically = true)와 동일하게, 벌크 UPDATE 후 영속성 컨텍스트를 비워
        // 이후 조회가 stale 캐시를 보지 않도록 한다.
        entityManager.clear();
        return (int) updated;
    }
}
