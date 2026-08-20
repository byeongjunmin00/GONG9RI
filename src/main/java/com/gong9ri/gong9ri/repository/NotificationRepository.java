package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.Notification;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // 마이페이지 "알림 목록" 조회용 — idx_member(member_id) 인덱스 활용.
    // 페이지네이션 도입(2026-08-20) 이후 화면 조회는 아래 Page 버전을 쓴다. 이 메서드는 테스트에서
    // "그 회원의 알림 전부"를 확인·정리할 때 계속 쓰이므로 남겨둔다.
    List<Notification> findAllByMemberIdOrderByCreatedAtDesc(Long memberId);

    // 알림 벨 목록 — 최신순 페이지 조회(2026-08-20). 알림 종류가 9종으로 늘면서 목록이 무한정
    // 길어질 수 있어 한 번에 다 내리지 않는다.
    Page<Notification> findByMemberIdOrderByCreatedAtDesc(Long memberId, Pageable pageable);

    // 안 읽은 알림 개수 — 반드시 "잘린 목록"이 아니라 DB에서 직접 센다. 프론트가 받아온 목록에서
    // 세면 페이지네이션과 동시에 성립할 수 없다(안읽음 30개인데 20개만 받으면 20으로 세고, 그 20개를
    // 읽는 순간 뱃지가 0이 되지만 실제로는 10개가 남는다).
    long countByMemberIdAndIsReadFalse(Long memberId);

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

    // 관리자 강제 삭제(product/admin) — 장난성 게시물처럼 결제·리뷰가 붙어도 지워야 할 때만 쓴다.
    // 알림이 공구팀(relatedTeam)을 참조하므로 팀을 지우기 전에 먼저 정리한다.
    @Transactional
    void deleteByRelatedTeam_Product_Id(Long productId);
}
