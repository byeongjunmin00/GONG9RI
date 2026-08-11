package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.ChatInteractionLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatInteractionLogRepository extends JpaRepository<ChatInteractionLog, Long> {

    // 세션별 토큰 누적/모델별 대시보드(누적토큰·P95지연·에러율) 둘 다 이 프로젝트 데이터 규모에서는
    // 별도 집계 쿼리보다, 필요한 로그 목록을 가져와 서비스에서 한 번에 계산하는 편이 단순하다.
    List<ChatInteractionLog> findAllBySessionId(Long sessionId);

    List<ChatInteractionLog> findAllByModel(String model);
}
