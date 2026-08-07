package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.GroupBuyTeam;
import com.gong9ri.gong9ri.entity.TeamStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * GroupBuyTeamRepository의 QueryDSL 기반 커스텀 쿼리(비관적 락 + 원자적 벌크 UPDATE 포함).
 * 구현은 {@link GroupBuyTeamRepositoryImpl} 참고 — docs/dev/ongoing/querydsl-migration.md.
 * 팀 참가 동시성의 핵심 로직이라 락 모드·CASE 표현식이 원래 JPQL과 동일하게 동작해야 한다.
 */
public interface GroupBuyTeamRepositoryCustom {

    Optional<GroupBuyTeam> findByIdForUpdate(Long id);

    List<GroupBuyTeam> findAllBySellerIdWithProduct(Long sellerId);

    // 마감 체크 스케줄러의 스캔 쿼리 — idx_status_deadline(status, deadline) 인덱스를 활용한다.
    // id만 조회해 스캔 시점 스냅샷과 실제 락 획득 시점을 분리한다(락은 findByIdForUpdate로 팀별로 건다).
    List<Long> findIdsByStatusAndDeadlineBefore(TeamStatus status, LocalDateTime now);

    // team/join 원자적 UPDATE 전략용 — 비관적 락 없이 조건부 증가만으로 정원 초과를 막는다.
    // 영향받은 row 수(0 또는 1)로 성공/실패(TEAM_FULL)를 판정한다. docs/logs/team/crud/003-atomic-comparison.md 참고.
    int incrementIfCapacity(Long id);
}
