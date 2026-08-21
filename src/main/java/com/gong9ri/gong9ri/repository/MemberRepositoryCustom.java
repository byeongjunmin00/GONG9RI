package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MemberRepositoryCustom {

    /**
     * 관리자 회원 목록 조회 (검색어, 역할, 정지여부 동적 조건 페이징).
     */
    Page<Member> findAllForAdmin(Pageable pageable, String search, Role role, Boolean suspended);
}
