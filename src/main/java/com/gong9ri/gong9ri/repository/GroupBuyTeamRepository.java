package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.GroupBuyTeam;
import com.gong9ri.gong9ri.entity.TeamStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupBuyTeamRepository extends JpaRepository<GroupBuyTeam, Long>, GroupBuyTeamRepositoryCustom {

    List<GroupBuyTeam> findByProductIdAndStatus(Long productId, TeamStatus status);
}
