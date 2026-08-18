package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.Notification;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // 마이페이지 "알림 목록" 조회용 — idx_member(member_id) 인덱스 활용.
    List<Notification> findAllByMemberIdOrderByCreatedAtDesc(Long memberId);

    // 관리자 회원 삭제 — 다른 테이블이 참조하지 않는 leaf 데이터라 회원 삭제 시 함께 지운다(product/admin).
    @Transactional
    void deleteByMemberId(Long memberId);
}
