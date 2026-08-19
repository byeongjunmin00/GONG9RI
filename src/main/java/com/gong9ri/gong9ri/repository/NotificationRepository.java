package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.Notification;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // 마이페이지 "알림 목록" 조회용 — idx_member(member_id) 인덱스 활용.
    List<Notification> findAllByMemberIdOrderByCreatedAtDesc(Long memberId);

    // 알림 벨 "모두 읽음" — 안 읽은 것만 골라서 한 번의 UPDATE로 처리(건별로 읽어서 하나씩 markAsRead()
    // 하는 것보다 훨씬 적은 쿼리). idx_member 인덱스를 그대로 활용한다.
    // clearAutomatically: 벌크 UPDATE는 영속성 컨텍스트(1차 캐시)를 거치지 않고 DB를 직접 바꾸므로,
    // 안 비우면 같은 트랜잭션 안에서 그 전에 이미 로드된 Notification 엔티티를 다시 조회할 때
    // isRead=false로 캐시된 옛 값이 그대로 보인다(테스트로 실제 재현 후 발견).
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.member.id = :memberId AND n.isRead = false")
    void markAllAsReadByMemberId(@Param("memberId") Long memberId);

    // 관리자 회원 삭제 — 다른 테이블이 참조하지 않는 leaf 데이터라 회원 삭제 시 함께 지운다(product/admin).
    @Transactional
    void deleteByMemberId(Long memberId);
}
