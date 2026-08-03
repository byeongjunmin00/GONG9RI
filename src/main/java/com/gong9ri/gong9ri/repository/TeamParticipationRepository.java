package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.TeamParticipation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface TeamParticipationRepository extends JpaRepository<TeamParticipation, Long> {

    boolean existsByTeamIdAndMemberId(Long teamId, Long memberId);

    @Transactional
    void deleteByTeamId(Long teamId);
}
