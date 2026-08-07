package com.gong9ri.gong9ri.repository;

import static com.gong9ri.gong9ri.entity.QTeamParticipation.teamParticipation;

import com.gong9ri.gong9ri.entity.TeamParticipation;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.util.List;

public class TeamParticipationRepositoryImpl implements TeamParticipationRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public TeamParticipationRepositoryImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Override
    public List<TeamParticipation> findAllByMemberIdWithTeamAndProduct(Long memberId) {
        return queryFactory
                .selectFrom(teamParticipation)
                .join(teamParticipation.team).fetchJoin()
                .join(teamParticipation.team.product).fetchJoin()
                .where(teamParticipation.member.id.eq(memberId))
                .orderBy(teamParticipation.joinedAt.desc())
                .fetch();
    }
}
