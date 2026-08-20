package com.gong9ri.gong9ri.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.interceptor.BeanFactoryCacheOperationSourceAdvisor;
import org.springframework.transaction.interceptor.BeanFactoryTransactionAttributeSourceAdvisor;

/**
 * 캐싱 AOP 어드바이저가 트랜잭션 어드바이저보다 항상 바깥쪽(더 낮은 order)에 있는지 검증한다
 * (docs/dev/product/seller-trust/changes/003-cache-evict-transaction-ordering.md). 순서가 뒤집히면
 * (또는 둘 다 기본값 동률이면) 캐시 무효화가 트랜잭션 커밋보다 먼저 실행될 수 있어, 커밋 전에 캐시가
 * 비고 그 틈에 동시 조회가 아직 커밋 안 된 옛 값으로 캐시를 다시 채우는 레이스가 생길 수 있다 —
 * `ReviewService`/`ProductService`가 같은 메서드에 `@Transactional`과 `@CacheEvict`를 함께 쓰는 모든
 * 곳에 적용되는 구조적 보장이라, 특정 서비스 테스트가 아니라 이 설정 자체를 검증한다.
 */
@SpringBootTest
class CacheEvictionOrderingTest {

    @Autowired
    private BeanFactoryCacheOperationSourceAdvisor cacheAdvisor;

    @Autowired
    private BeanFactoryTransactionAttributeSourceAdvisor transactionAdvisor;

    @Test
    @DisplayName("캐싱 어드바이저가 트랜잭션 어드바이저보다 order가 낮다(더 바깥쪽 = 캐시 무효화가 커밋 이후에 실행)")
    void cacheAdvisorWrapsOutsideTransactionAdvisor() {
        assertTrue(cacheAdvisor.getOrder() < transactionAdvisor.getOrder(),
                "캐싱 어드바이저(order=" + cacheAdvisor.getOrder()
                        + ")가 트랜잭션 어드바이저(order=" + transactionAdvisor.getOrder() + ")보다 바깥쪽이어야 한다");
    }
}
