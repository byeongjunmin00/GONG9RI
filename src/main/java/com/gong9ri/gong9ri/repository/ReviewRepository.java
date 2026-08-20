package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.Review;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface ReviewRepository extends JpaRepository<Review, Long>, ReviewRepositoryCustom {

    List<Review> findByProductIdOrderByCreatedAtDesc(Long productId);

    boolean existsByProductIdAndMemberId(Long productId, Long memberId);

    // 관리자 회원 삭제 — 작성한 리뷰가 하나라도 있으면 하드 삭제를 막는다(product/admin).
    boolean existsByMemberId(Long memberId);

    // 상품 삭제 가드(product/admin) — 그 상품에 달린 리뷰가 하나라도 있으면 삭제를 막는다.
    boolean existsByProductId(Long productId);

    // 관리자 강제 삭제(product/admin) — 장난성 게시물처럼 결제·리뷰가 붙어도 지워야 할 때만 쓴다.
    @Transactional
    void deleteByProductId(Long productId);
}
