package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.TeamParticipation;
import java.util.List;

/**
 * TeamParticipationRepository의 QueryDSL 기반 커스텀 쿼리(페치조인).
 * 구현은 {@link TeamParticipationRepositoryImpl} 참고 — docs/dev/ongoing/querydsl-migration.md.
 */
public interface TeamParticipationRepositoryCustom {

    List<TeamParticipation> findAllByMemberIdWithTeamAndProduct(Long memberId);

    /**
     * 특정 팀의 참여자 전원을 회원(작성자)·팀(리더 판정용)까지 fetch join으로 한 번에 가져온다(N+1 방지).
     * joinedAt 오름차순으로 반환 — "리더 우선" 정렬은 리더 여부가 이 쿼리 결과만으로는 최종 순서를 정할 수
     * 없어(리더도 참여 순서상 항상 첫 행이 아닐 수 있는 미래 확장을 배제하지 않기 위해) 서비스 계층에서
     * 안정 정렬(stable sort)로 마무리한다 — docs/dev/team/crud/design.md 참고.
     */
    List<TeamParticipation> findAllByTeamIdWithMemberOrderByJoinedAtAsc(Long teamId);
}
