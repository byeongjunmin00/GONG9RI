package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.TeamParticipation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface TeamParticipationRepository extends JpaRepository<TeamParticipation, Long> {

    boolean existsByTeamIdAndMemberId(Long teamId, Long memberId);

    @Transactional
    void deleteByTeamId(Long teamId);

    @Query("SELECT tp FROM TeamParticipation tp JOIN FETCH tp.team t JOIN FETCH t.product "
            + "WHERE tp.member.id = :memberId ORDER BY tp.joinedAt DESC")
    List<TeamParticipation> findAllByMemberIdWithTeamAndProduct(@Param("memberId") Long memberId);
}
