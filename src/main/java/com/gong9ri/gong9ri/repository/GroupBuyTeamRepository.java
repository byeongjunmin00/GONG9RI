package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.GroupBuyTeam;
import com.gong9ri.gong9ri.entity.TeamStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface GroupBuyTeamRepository extends JpaRepository<GroupBuyTeam, Long>, GroupBuyTeamRepositoryCustom {

    List<GroupBuyTeam> findByProductIdAndStatus(Long productId, TeamStatus status);

    // 메인 페이지 카드 진행바(product/list-progress) 전용 — 페이지에 실린 상품들의 RECRUITING 팀을
    // 한 번에 조회한다. 캐시하지 않는다(ProductSummaryResponse.activeTeamCurrentCount 주석 참고).
    List<GroupBuyTeam> findByProductIdInAndStatus(List<Long> productIds, TeamStatus status);

    // 관리자 회원 삭제 — 팀 리더로 신설한 공구팀이 하나라도 있으면 하드 삭제를 막는다(product/admin).
    boolean existsByLeader_Id(Long leaderId);

    // 상품 삭제 가드(product/admin) — 그 상품에 개설된 공구팀이 하나라도 있으면 삭제를 막는다.
    boolean existsByProduct_Id(Long productId);

    // 관리자 강제 삭제(product/admin) — 장난성 게시물처럼 결제·리뷰가 붙어도 지워야 할 때만 쓴다.
    @Transactional
    void deleteByProduct_Id(Long productId);

    // 공구팀 번호 백필(admin-identifier-codes, IdentifierCodeBackfillService) — 이 컬럼이 nullable인
    // 동안 아직 채번되지 않은 기존 행만 골라낸다.
    @Query("SELECT t.id FROM GroupBuyTeam t WHERE t.teamNo IS NULL")
    List<Long> findIdsByTeamNoIsNull();
}
