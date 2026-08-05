package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.GroupBuyTeam;
import com.gong9ri.gong9ri.entity.TeamStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupBuyTeamRepository extends JpaRepository<GroupBuyTeam, Long> {

    List<GroupBuyTeam> findByProductIdAndStatus(Long productId, TeamStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM GroupBuyTeam t WHERE t.id = :id")
    Optional<GroupBuyTeam> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT t FROM GroupBuyTeam t JOIN FETCH t.product p WHERE p.seller.id = :sellerId ORDER BY t.createdAt DESC")
    List<GroupBuyTeam> findAllBySellerIdWithProduct(@Param("sellerId") Long sellerId);

    // 마감 체크 스케줄러의 스캔 쿼리 — idx_status_deadline(status, deadline) 인덱스를 활용한다.
    // id만 조회해 스캔 시점 스냅샷과 실제 락 획득 시점을 분리한다(락은 findByIdForUpdate로 팀별로 건다).
    @Query("SELECT t.id FROM GroupBuyTeam t WHERE t.status = :status AND t.deadline < :now")
    List<Long> findIdsByStatusAndDeadlineBefore(@Param("status") TeamStatus status, @Param("now") LocalDateTime now);

    // team/join 원자적 UPDATE 전략용 — 비관적 락 없이 조건부 증가만으로 정원 초과를 막는다.
    // 영향받은 row 수(0 또는 1)로 성공/실패(TEAM_FULL)를 판정한다. docs/logs/team/crud/003-atomic-comparison.md 참고.
    @Modifying(clearAutomatically = true)
    @Query("UPDATE GroupBuyTeam t SET "
            + "t.currentCount = t.currentCount + 1, "
            + "t.status = CASE WHEN t.currentCount + 1 = t.maxParticipants "
            + "THEN com.gong9ri.gong9ri.entity.TeamStatus.SUCCESS ELSE t.status END, "
            + "t.updatedAt = CURRENT_TIMESTAMP "
            + "WHERE t.id = :id AND t.currentCount < t.maxParticipants")
    int incrementIfCapacity(@Param("id") Long id);
}
