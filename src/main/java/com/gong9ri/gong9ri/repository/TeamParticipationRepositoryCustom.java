package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.TeamParticipation;
import java.util.List;

/**
 * TeamParticipationRepository의 QueryDSL 기반 커스텀 쿼리(페치조인).
 * 구현은 {@link TeamParticipationRepositoryImpl} 참고 — docs/dev/ongoing/querydsl-migration.md.
 */
public interface TeamParticipationRepositoryCustom {

    List<TeamParticipation> findAllByMemberIdWithTeamAndProduct(Long memberId);
}
