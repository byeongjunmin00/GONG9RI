package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.dto.ProductSort;
import com.gong9ri.gong9ri.entity.Product;
import com.gong9ri.gong9ri.entity.ProductCategory;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * ProductRepository의 QueryDSL 기반 커스텀 쿼리(페치조인 포함).
 * 구현은 {@link ProductRepositoryImpl} 참고 — docs/dev/ongoing/querydsl-migration.md.
 */
public interface ProductRepositoryCustom {

    // category가 null이면 필터 조건 없이 전체 조회한다(메인 페이지 "전체" 탭, product/category).
    // sort가 POPULAR면 RECRUITING 팀 중 참여 인원이 가장 많은 팀 기준 내림차순, null이면 정렬 없음
    // (product/list-sort).
    Page<Product> findAllWithSeller(Pageable pageable, ProductCategory category, ProductSort sort);

    Optional<Product> findByIdWithSeller(Long id);
}
