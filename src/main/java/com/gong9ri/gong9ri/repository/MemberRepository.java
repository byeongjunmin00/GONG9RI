package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Role;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByUsername(String username);

    // 관리자 대시보드(product/admin) 요약 카드용.
    long countByRole(Role role);

    boolean existsByEmail(String email);

    // 정보수정(이메일 변경) 시 "본인 제외" 중복 검사용 — 자기 자신의 기존 이메일과 비교해서
    // 거부되면 안 되므로 existsByEmail 대신 이걸 쓴다.
    boolean existsByEmailAndIdNot(String email, Long id);

    Optional<Member> findByUsername(String username);

    Optional<Member> findByEmail(String email);

    Optional<Member> findByKakaoId(String kakaoId);

    // 고객센터 상담 알림(support/chat) — 관리자 전원에게 보낸다. 지금은 한 명(demo_admin)이지만
    // 계정이 늘어도 코드가 그대로 동작하도록 목록으로 받는다. 알림은 id만 쓰므로 프로젝션으로 뽑는다.
    @Query("SELECT m.id FROM Member m WHERE m.role = :role")
    List<Long> findIdsByRole(@Param("role") Role role);
}
