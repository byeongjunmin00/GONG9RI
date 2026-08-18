package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.Wishlist;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WishlistRepository extends JpaRepository<Wishlist, Long>, WishlistRepositoryCustom {

    boolean existsByMember_IdAndProduct_Id(Long memberId, Long productId);

    void deleteByMember_IdAndProduct_Id(Long memberId, Long productId);

    // 관리자 회원 삭제 — 찜한 상품이 하나라도 있으면 하드 삭제를 막는다(product/admin).
    boolean existsByMember_Id(Long memberId);
}
