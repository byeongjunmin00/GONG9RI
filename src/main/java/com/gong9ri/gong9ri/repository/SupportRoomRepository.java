package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.SupportRoom;
import com.gong9ri.gong9ri.entity.SupportRoomStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface SupportRoomRepository extends JpaRepository<SupportRoom, Long> {

    // "한 회원당 열린 방 1개" 불변식 확인용. 닫힌 방은 여러 개일 수 있어 DB 유니크 제약으로는 못 건다.
    Optional<SupportRoom> findByMember_IdAndStatus(Long memberId, SupportRoomStatus status);

    // 관리자 상담 목록 — 답을 기다리는 방(미읽음 있는 방)이 위로 오고, 그다음 최근 대화순.
    // 회원을 fetch join하는 건 목록에 이름을 띄우기 때문이다(안 하면 방 수만큼 쿼리가 더 나간다).
    @Query(value = "SELECT r FROM SupportRoom r JOIN FETCH r.member "
            + "ORDER BY CASE WHEN r.unreadForAdmin > 0 THEN 0 ELSE 1 END, r.lastMessageAt DESC",
            countQuery = "SELECT COUNT(r) FROM SupportRoom r")
    Page<SupportRoom> findAllForAdmin(Pageable pageable);

    long countByUnreadForAdminGreaterThan(int threshold);

    // 관리자 회원 삭제(product/admin) — 상담 기록은 그 회원에 종속된 데이터라 함께 정리한다.
    @Transactional
    void deleteByMember_Id(Long memberId);
}
