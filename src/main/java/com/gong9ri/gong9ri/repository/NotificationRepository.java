package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.Notification;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // 마이페이지 "알림 목록" 조회용 — idx_member(member_id) 인덱스 활용.
    List<Notification> findAllByMemberIdOrderByCreatedAtDesc(Long memberId);
}
