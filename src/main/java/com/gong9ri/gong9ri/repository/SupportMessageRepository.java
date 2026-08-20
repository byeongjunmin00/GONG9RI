package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.SupportMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {

    // 대화는 오래된 것부터 보여준다. 보낸 사람을 fetch join하는 건 이름을 함께 내리기 때문.
    @Query("SELECT m FROM SupportMessage m JOIN FETCH m.sender WHERE m.room.id = :roomId ORDER BY m.createdAt ASC")
    List<SupportMessage> findByRoomIdWithSender(@Param("roomId") Long roomId);

    @Transactional
    void deleteByRoom_Id(Long roomId);

    // 관리자 회원 삭제 — 방보다 먼저 지워야 한다(메시지가 방을 참조).
    @Transactional
    @Query("DELETE FROM SupportMessage m WHERE m.room.id IN (SELECT r.id FROM SupportRoom r WHERE r.member.id = :memberId)")
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
    void deleteByRoomOwner(@Param("memberId") Long memberId);
}
