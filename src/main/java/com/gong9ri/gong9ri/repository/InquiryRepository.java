package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.Inquiry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    // 질문자를 함께 가져온다 — InquiryResponse가 질문자 이름과 프로필 사진을 싣기 때문에, fetch join이
    // 없으면 문의 N건마다 member SELECT가 한 번씩 더 나간다(리뷰 목록과 같은 이유).
    @Query("SELECT i FROM Inquiry i JOIN FETCH i.member WHERE i.product.id = :productId ORDER BY i.createdAt DESC")
    List<Inquiry> findByProductIdOrderByCreatedAtDesc(@Param("productId") Long productId);

    // 관리자 회원 삭제 — 작성한 문의 또는(판매자로서) 답변한 문의가 하나라도 있으면 하드 삭제를
    // 막는다(product/admin). 두 역할을 한 쿼리로 같이 본다.
    boolean existsByMember_IdOrAnsweredBy_Id(Long memberId, Long answeredById);

    // 상품 삭제(product/admin) — 문의는 그 상품에 대한 질문이라 상품이 사라지면 의미가 없다.
    // 다른 테이블이 이 행을 참조하지 않으므로(답변은 같은 행의 컬럼) 함께 지운다.
    @Transactional
    void deleteByProduct_Id(Long productId);
}
