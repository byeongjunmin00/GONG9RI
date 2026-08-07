package com.gong9ri.gong9ri.repository;

import com.gong9ri.gong9ri.entity.Product;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * ProductRepository의 QueryDSL 기반 커스텀 쿼리(페치조인 포함).
 * 구현은 {@link ProductRepositoryImpl} 참고 — docs/dev/ongoing/querydsl-migration.md.
 */
public interface ProductRepositoryCustom {

    Page<Product> findAllWithSeller(Pageable pageable);

    Optional<Product> findByIdWithSeller(Long id);
}
