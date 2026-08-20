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
    // (product/list-sort). keyword가 있으면 상품명 또는 판매자명에 포함된 것만(대소문자 무시,
    // product/list-search).
    // openSoon(product/list-enhancements의 오픈예정 탭, docs/dev/product/list-enhancements/design.md) —
    // true면 openAt이 설정돼 있고 아직 미래인 상품만 반환한다. false이면서 category가 지정된 경우
    // (특정 카테고리 탭 조회)는 반대로 아직 공개 전인 상품을 제외한다 — 오픈예정 상품은 오픈 시각이
    // 지나기 전까지 자신의 실제 카테고리 탭에는 노출되지 않는다. category가 없고 openSoon도 아니면
    // (전체 탭, 카테고리 미지정 검색) 오픈예정 여부와 무관하게 기존과 동일하게 전부 포함한다.
    Page<Product> findAllWithSeller(Pageable pageable, ProductCategory category, ProductSort sort, String keyword,
            boolean openSoon);

    // 관리자 상품 현황(product/admin) — 숨김 상품까지 포함해 전부 조회한다.
    Page<Product> findAllForAdmin(Pageable pageable);

    Optional<Product> findByIdWithSeller(Long id);
}
