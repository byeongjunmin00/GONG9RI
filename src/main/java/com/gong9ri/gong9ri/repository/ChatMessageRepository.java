package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.ChatMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findAllBySessionIdOrderByCreatedAtAsc(Long sessionId);

    // 최근 N턴 윈도우(design.md 참고) — 최신순으로 N개만 가져온 뒤 서비스에서 시간순으로 뒤집어 프롬프트에 넣는다.
    List<ChatMessage> findTop10BySessionIdOrderByCreatedAtDesc(Long sessionId);
}
