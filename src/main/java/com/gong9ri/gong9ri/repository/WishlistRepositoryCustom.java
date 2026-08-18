package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.Wishlist;
import java.util.List;

public interface WishlistRepositoryCustom {

    // 구매자 마이페이지 "찜한 상품" 목록 — 상품·판매자까지 fetch join(N+1 방지).
    List<Wishlist> findAllByMemberIdWithProduct(Long memberId);
}
