package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.Wishlist;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WishlistRepository extends JpaRepository<Wishlist, Long>, WishlistRepositoryCustom {

    boolean existsByMember_IdAndProduct_Id(Long memberId, Long productId);

    void deleteByMember_IdAndProduct_Id(Long memberId, Long productId);
}
