package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Role;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
