package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    // 관리자 회원 삭제 — 챗봇 세션이 하나라도 있으면 하드 삭제를 막는다(product/admin). ChatSession은
    // ChatMessage/ChatInteractionLog가 더 참조하고 있어(레코드 3단 체인) 여기서 함께 지우지 않고,
    // "활동 있음"으로 간주해 삭제 자체를 막는 쪽을 택했다 — 정지(suspend)로 유도.
    boolean existsByBuyer_Id(Long buyerId);
}
