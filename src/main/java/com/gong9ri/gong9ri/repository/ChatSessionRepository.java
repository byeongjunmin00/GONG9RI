package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
}
