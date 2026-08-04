package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.GroupBuyTeam;
import com.gong9ri.gong9ri.entity.TeamStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupBuyTeamRepository extends JpaRepository<GroupBuyTeam, Long> {

    List<GroupBuyTeam> findByProductIdAndStatus(Long productId, TeamStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM GroupBuyTeam t WHERE t.id = :id")
    Optional<GroupBuyTeam> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT t FROM GroupBuyTeam t JOIN FETCH t.product p WHERE p.seller.id = :sellerId ORDER BY t.createdAt DESC")
    List<GroupBuyTeam> findAllBySellerIdWithProduct(@Param("sellerId") Long sellerId);
}
