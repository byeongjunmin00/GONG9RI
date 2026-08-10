package com.gong9ri.gong9ri.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.gong9ri.gong9ri.common.exception.BusinessException;
import com.gong9ri.gong9ri.common.exception.ErrorCode;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.dto.PriceTierRequest;
import com.gong9ri.gong9ri.dto.ProductPageResponse;
import com.gong9ri.gong9ri.dto.ProductRegisterRequest;
import com.gong9ri.gong9ri.dto.ProductResponse;
import com.gong9ri.gong9ri.dto.ProductSummaryResponse;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Product;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.repository.MemberRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * product/list, product/detail 캐싱 동작 검증 (docs/dev/ongoing/product-list-detail-caching.md).
 * 테스트는 src/test/resources/application.yaml에서 spring.cache.type=simple(ConcurrentMapCacheManager)로
 * 오버라이드해, 실제 Redis 연결 없이도 @Cacheable/@CacheEvict/@Caching 무효화 동작 자체를 검증한다.
 *
 * SellerRevenueCachingTest와 동일한 패턴을 따른다 — 의미 있는 규모의 더미 데이터를 심고,
 * 캐시 히트 시나리오는 무효화 경로(register/update/delete)를 거치지 않고 레포지토리에 직접
 * 데이터를 꽂아 넣어 재조회 값이 그 변경을 반영하지 않고 이전 값 그대로임을 확인해
 * "진짜로 캐시된 값"임을 증명한다.
 */
@SpringBootTest
@Transactional
class ProductCachingTest {

    private static final int DUMMY_PRODUCT_COUNT = 15;
    private static final int LIST_PAGE = 0;
    // 목록 캐시 키는 page+size 조합뿐이라(seller/데이터셋과 무관), 같은 Spring 컨텍스트를 공유하는
    // 다른 테스트 메서드와 캐시 키가 겹치면 서로 다른 테스트의 캐시값을 침범해 읽는다
    // (DB는 @Transactional로 테스트별 롤백되지만, 캐시 빈은 테스트 클래스/메서드 간 공유되는 싱글톤이라
    // 트랜잭션 롤백과 무관하게 상태가 남는다). 그래서 테스트 메서드마다 size를 다르게 써서 키를 격리한다.

    @Autowired
    private ProductService productService;

    @Autowired
    private MemberRepository memberRepository;

    @MockitoSpyBean
    private ProductRepository productRepository;

    private Member saveMember(String username, Role role) {
        return memberRepository.save(new Member(username, "pw", "테스트유저", username + "@test.com", role));
    }

    private Product saveProduct(Member seller, String name, Integer basePrice) {
        return productRepository.save(new Product(seller, name, "설명", basePrice, 10, null));
    }

    private MemberUserDetails asPrincipal(Member member) {
        return new MemberUserDetails(member);
    }

    /** 유의미한 규모(DUMMY_PRODUCT_COUNT개)의 더미 상품을 판매자 아래 직접 심는다. */
    private void seedDummyProducts(Member seller, int count) {
        for (int i = 1; i <= count; i++) {
            saveProduct(seller, "더미상품" + i, 1000 * i);
        }
    }

    private ProductRegisterRequest registerRequest(String name, Integer basePrice) {
        return new ProductRegisterRequest(name, "설명", basePrice, 10,
                List.of(new PriceTierRequest(2, basePrice - 1000)), null);
    }

    @Test
    @DisplayName("동일 page/size 반복 조회는 레포지토리를 우회해 데이터가 바뀌어도 캐시된 이전 값을 그대로 반환한다 (진짜 캐시 히트)")
    void list_secondCall_returnsStaleCachedValue_evenAfterUnderlyingDataChangesOutsideOfInvalidation() {
        int size = 101;
        Member seller = saveMember("listCacheSeller1", Role.SELLER);
        seedDummyProducts(seller, DUMMY_PRODUCT_COUNT);

        ProductPageResponse first = productService.list(LIST_PAGE, size);
        assertEquals(DUMMY_PRODUCT_COUNT, first.totalElements());

        // 캐시 무효화 경로(register/update/delete)를 거치지 않고 레포지토리에 직접 상품을 꽂아 넣는다 —
        // 캐시가 진짜로 이전 값을 들고 있는지 증명하기 위한 대조군.
        saveProduct(seller, "무효화안된새상품", 99000);

        ProductPageResponse second = productService.list(LIST_PAGE, size);

        assertEquals(first, second);
        assertNotEquals(DUMMY_PRODUCT_COUNT + 1, second.totalElements());
        verify(productRepository, times(1)).findAllWithSeller(ArgumentMatchers.any());
    }

    @Test
    @DisplayName("동일 productId 반복 조회는 레포지토리를 우회해 데이터가 바뀌어도 캐시된 이전 값을 그대로 반환한다 (진짜 캐시 히트)")
    void detail_secondCall_returnsStaleCachedValue_evenAfterUnderlyingDataChangesOutsideOfInvalidation() {
        Member seller = saveMember("detailCacheSeller1", Role.SELLER);
        Product product = saveProduct(seller, "원래상품명", 20000);

        ProductResponse first = productService.detail(product.getId());
        assertEquals("원래상품명", first.name());

        // 무효화 경로(update)를 거치지 않고 엔티티를 직접 변경해 저장한다.
        Product directlyModified = productRepository.findById(product.getId()).orElseThrow();
        directlyModified.update("무효화안된이름", "설명", 77000, 10, null);
        productRepository.save(directlyModified);

        ProductResponse second = productService.detail(product.getId());

        assertEquals(first, second);
        assertNotEquals("무효화안된이름", second.name());
        verify(productRepository, times(1)).findByIdWithSeller(product.getId());
    }

    @Test
    @DisplayName("register 이후 목록 재조회 시 새 상품이 반영된다 (무효화 확인)")
    void list_afterRegister_cacheEvictedAndReflectsNewProduct() {
        int size = 102;
        Member seller = saveMember("registerCacheSeller1", Role.SELLER);
        seedDummyProducts(seller, DUMMY_PRODUCT_COUNT);

        ProductPageResponse before = productService.list(LIST_PAGE, size);
        assertEquals(DUMMY_PRODUCT_COUNT, before.totalElements());

        productService.register(asPrincipal(seller), registerRequest("신규등록상품", 30000));

        ProductPageResponse after = productService.list(LIST_PAGE, size);
        assertNotEquals(before, after);
        assertEquals(DUMMY_PRODUCT_COUNT + 1, after.totalElements());
        assertTrue(after.content().stream()
                .map(ProductSummaryResponse::name)
                .anyMatch("신규등록상품"::equals));

        verify(productRepository, times(2)).findAllWithSeller(ArgumentMatchers.any());
    }

    @Test
    @DisplayName("update 이후 해당 상품 상세·목록 재조회 시 최신 값이 반영된다")
    void detailAndList_afterUpdate_cacheEvictedAndReflectsLatest() {
        int size = 103;
        Member seller = saveMember("updateCacheSeller1", Role.SELLER);
        seedDummyProducts(seller, DUMMY_PRODUCT_COUNT);
        Product target = saveProduct(seller, "수정전이름", 15000);

        ProductResponse detailBefore = productService.detail(target.getId());
        ProductPageResponse listBefore = productService.list(LIST_PAGE, size);
        assertEquals("수정전이름", detailBefore.name());

        productService.update(asPrincipal(seller), target.getId(), registerRequest("수정후이름", 40000));

        ProductResponse detailAfter = productService.detail(target.getId());
        ProductPageResponse listAfter = productService.list(LIST_PAGE, size);

        assertNotEquals(detailBefore, detailAfter);
        assertEquals("수정후이름", detailAfter.name());
        assertEquals(40000, detailAfter.basePrice());

        assertNotEquals(listBefore, listAfter);
        assertTrue(listAfter.content().stream()
                .map(ProductSummaryResponse::name)
                .anyMatch("수정후이름"::equals));
        assertFalse(listAfter.content().stream()
                .map(ProductSummaryResponse::name)
                .anyMatch("수정전이름"::equals));

        // findByIdWithSeller는 3번 호출된다: detailBefore(캐시 미스) + update() 내부의
        // findProductWithSeller(소유자 검증용, 캐시와 무관한 평범한 조회) + detailAfter(무효화 후 캐시 미스).
        verify(productRepository, times(3)).findByIdWithSeller(target.getId());
        verify(productRepository, times(2)).findAllWithSeller(ArgumentMatchers.any());
    }

    @Test
    @DisplayName("delete 이후 목록 재조회 시 해당 상품이 빠지고, 상세 재조회 시 404 PRODUCT_NOT_FOUND")
    void listAndDetail_afterDelete_cacheEvictedAndProductRemoved() {
        int size = 104;
        Member seller = saveMember("deleteCacheSeller1", Role.SELLER);
        seedDummyProducts(seller, DUMMY_PRODUCT_COUNT);
        Product target = saveProduct(seller, "삭제될상품", 12000);

        ProductPageResponse listBefore = productService.list(LIST_PAGE, size);
        assertTrue(listBefore.content().stream()
                .map(ProductSummaryResponse::name)
                .anyMatch("삭제될상품"::equals));
        productService.detail(target.getId());

        productService.delete(asPrincipal(seller), target.getId());

        ProductPageResponse listAfter = productService.list(LIST_PAGE, size);
        assertFalse(listAfter.content().stream()
                .map(ProductSummaryResponse::name)
                .anyMatch("삭제될상품"::equals));
        assertEquals(DUMMY_PRODUCT_COUNT, listAfter.totalElements());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> productService.detail(target.getId()));
        assertEquals(ErrorCode.PRODUCT_NOT_FOUND, exception.getErrorCode());

        verify(productRepository, times(2)).findAllWithSeller(ArgumentMatchers.any());
    }
}
