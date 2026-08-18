package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.Review;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long>, ReviewRepositoryCustom {

    List<Review> findByProductIdOrderByCreatedAtDesc(Long productId);

    boolean existsByProductIdAndMemberId(Long productId, Long memberId);

    // 관리자 회원 삭제 — 작성한 리뷰가 하나라도 있으면 하드 삭제를 막는다(product/admin).
    boolean existsByMemberId(Long memberId);
}
