package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.Product;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long>, ProductRepositoryCustom {

    List<Product> findAllBySellerIdOrderByCreatedAtDesc(Long sellerId);

    // 챗봇 상품검색 Tool 전용 — 정확한 카탈로그 페이지네이션이 아니라 채팅 답변에 쓸 후보 몇 개면 충분해서 상위 10개로 제한.
    List<Product> findTop10ByNameContainingIgnoreCase(String keyword);

    // 관리자 회원 삭제 — 판매자로서 등록한 상품이 하나라도 있으면 하드 삭제를 막는다(product/admin).
    boolean existsBySeller_Id(Long sellerId);
}
