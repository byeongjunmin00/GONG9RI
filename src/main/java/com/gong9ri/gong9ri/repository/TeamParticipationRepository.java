package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.TeamParticipation;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface TeamParticipationRepository
        extends JpaRepository<TeamParticipation, Long>, TeamParticipationRepositoryCustom {

    boolean existsByTeamIdAndMemberId(Long teamId, Long memberId);

    @Transactional
    void deleteByTeamId(Long teamId);

    // 참여 취소(team/leave) — 취소한 사람의 참여 기록만 제거한다. team_participation의 기존
    // "하드 삭제 없음" 정책(docs/db/team_participation.md)은 이 기능으로 갱신됐다 — 참여 취소는 자리
    // 반환이 핵심이라 즉시 삭제가 맞고, 돈이 오간 이력은 이 테이블이 아니라 payment/refund_request가
    // 보존한다.
    @Transactional
    void deleteByTeamIdAndMemberId(Long teamId, Long memberId);

    // 리더가 참여를 취소했을 때 그다음 최초 참가자(joinedAt 가장 빠른 사람)에게 리더를 승계하기 위한 조회.
    Optional<TeamParticipation> findFirstByTeamIdOrderByJoinedAtAsc(Long teamId);
}
