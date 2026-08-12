package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.Member;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Optional<Member> findByUsername(String username);

    Optional<Member> findByEmail(String email);
}
