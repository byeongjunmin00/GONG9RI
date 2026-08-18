package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.AiSuggestionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface AiSuggestionLogRepository extends JpaRepository<AiSuggestionLog, Long> {

    // 관리자 회원 삭제 — 다른 테이블이 참조하지 않는 leaf 데이터라 회원 삭제 시 함께 지운다(product/admin).
    @Transactional
    void deleteBySeller_Id(Long sellerId);
}
