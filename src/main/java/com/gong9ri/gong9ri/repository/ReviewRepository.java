package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.Review;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ReviewRepository extends JpaRepository<Review, Long>, ReviewRepositoryCustom {

    // 작성자를 함께 가져온다 — ReviewResponse가 작성자 이름과 프로필 사진을 싣기 때문에, fetch join이
    // 없으면 리뷰 N건마다 member SELECT가 한 번씩 더 나간다(리뷰 20개면 쿼리 21번).
    @Query("SELECT r FROM Review r JOIN FETCH r.member WHERE r.product.id = :productId ORDER BY r.createdAt DESC")
    List<Review> findByProductIdOrderByCreatedAtDesc(@Param("productId") Long productId);

    boolean existsByProductIdAndMemberId(Long productId, Long memberId);

    // 관리자 회원 삭제 — 작성한 리뷰가 하나라도 있으면 하드 삭제를 막는다(product/admin).
    boolean existsByMemberId(Long memberId);

    // 상품 삭제 가드(product/admin) — 그 상품에 달린 리뷰가 하나라도 있으면 삭제를 막는다.
    boolean existsByProductId(Long productId);

    // 관리자 강제 삭제(product/admin) — 장난성 게시물처럼 결제·리뷰가 붙어도 지워야 할 때만 쓴다.
    @Transactional
    void deleteByProductId(Long productId);
}
