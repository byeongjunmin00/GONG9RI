package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByUsername(String username);
}
