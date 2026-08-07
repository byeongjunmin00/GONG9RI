package com.gong9ri.gong9ri.repository;

/**
 * {@link BestPriceProjection}의 QueryDSL {@code Projections.constructor} 대상 구현체.
 * QueryDSL은 인터페이스 프로젝션(bean binding)을 직접 지원하지 않아, 생성자 프로젝션이 바인딩할
 * 구체 클래스가 필요하다 — {@link PriceTierRepositoryImpl} 참고.
 */
public class BestPriceProjectionImpl implements BestPriceProjection {

    private final Long productId;
    private final Integer bestPrice;

    public BestPriceProjectionImpl(Long productId, Integer bestPrice) {
        this.productId = productId;
        this.bestPrice = bestPrice;
    }

    @Override
    public Long getProductId() {
        return productId;
    }

    @Override
    public Integer getBestPrice() {
        return bestPrice;
    }
}
