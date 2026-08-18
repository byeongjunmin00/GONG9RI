package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.GroupBuyTeam;
import com.gong9ri.gong9ri.entity.TeamStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupBuyTeamRepository extends JpaRepository<GroupBuyTeam, Long>, GroupBuyTeamRepositoryCustom {

    List<GroupBuyTeam> findByProductIdAndStatus(Long productId, TeamStatus status);

    // 메인 페이지 카드 진행바(product/list-progress) 전용 — 페이지에 실린 상품들의 RECRUITING 팀을
    // 한 번에 조회한다. 캐시하지 않는다(ProductSummaryResponse.activeTeamCurrentCount 주석 참고).
    List<GroupBuyTeam> findByProductIdInAndStatus(List<Long> productIds, TeamStatus status);
}
