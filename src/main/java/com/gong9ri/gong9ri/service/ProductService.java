package com.gong9ri.gong9ri.service;

import com.gong9ri.gong9ri.common.exception.BusinessException;
import com.gong9ri.gong9ri.common.exception.ErrorCode;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.config.CacheConfig;
import com.gong9ri.gong9ri.dto.PriceTierRequest;
import com.gong9ri.gong9ri.dto.ProductPageResponse;
import com.gong9ri.gong9ri.dto.ProductRegisterRequest;
import com.gong9ri.gong9ri.dto.ProductResponse;
import com.gong9ri.gong9ri.dto.ProductSummaryResponse;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.PriceTier;
import com.gong9ri.gong9ri.entity.Product;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.repository.BestPriceProjection;
import com.gong9ri.gong9ri.repository.PriceTierRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final PriceTierRepository priceTierRepository;

    // 조회 빈도가 높고 등록/수정/삭제 전까지 안 변해 캐싱 효과가 크다 (docs/policy/caching.md).
    // 정렬 조건(ORDER BY)이 없어 새 상품이 어느 페이지에 들어갈지 특정할 수 없으므로,
    // 무효화는 특정 키가 아니라 이 캐시 전체를 대상으로 한다(register/update/delete).
    @Cacheable(cacheNames = CacheConfig.PRODUCT_LIST_CACHE, key = "#page + '-' + #size")
    public ProductPageResponse list(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Product> products = productRepository.findAllWithSeller(pageable);

        List<Long> productIds = products.getContent().stream().map(Product::getId).toList();
        Map<Long, Integer> bestPrices = productIds.isEmpty()
                ? Map.of()
                : priceTierRepository.findBestPricesByProductIds(productIds).stream()
                        .collect(Collectors.toMap(BestPriceProjection::getProductId, BestPriceProjection::getBestPrice));

        Page<ProductSummaryResponse> mapped = products.map(
                product -> ProductSummaryResponse.of(product, bestPrices.get(product.getId())));
        return ProductPageResponse.of(mapped);
    }

    // 상품 상세도 등록/수정/삭제 전까지 안 변해 productId 기준으로 캐싱한다 (docs/policy/caching.md).
    @Cacheable(cacheNames = CacheConfig.PRODUCT_DETAIL_CACHE, key = "#productId")
    public ProductResponse detail(Long productId) {
        Product product = findProductWithSeller(productId);
        List<PriceTier> priceTiers = priceTierRepository.findByProductIdOrderByMinCountAsc(productId);
        return ProductResponse.of(product, priceTiers);
    }

    // 신규 상품이 어느 페이지에 들어갈지 특정할 수 없어(ORDER BY 없음) 목록 캐시를 전체 무효화한다.
    // 신규 productId는 캐시에 아직 없어 첫 조회가 자연히 미스이므로 상세 캐시는 건드릴 필요 없다.
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.PRODUCT_LIST_CACHE, allEntries = true)
    public ProductResponse register(MemberUserDetails principal, ProductRegisterRequest request) {
        requireSeller(principal);
        Member seller = principal.getMember();

        Product product = new Product(seller, request.name(), request.description(),
                request.basePrice(), request.maxParticipants());
        Product saved = productRepository.save(product);

        List<PriceTier> priceTiers = savePriceTiers(saved, request.priceTiers());
        log.info("상품 등록 완료: productId={}, sellerId={}", saved.getId(), seller.getId());
        return ProductResponse.of(saved, priceTiers);
    }

    // 이름/가격 등이 바뀌면 이 상품이 포함된 목록 페이지가 달라질 수 있어(어느 페이지인지 특정 불가) 목록 캐시도
    // 함께 전체 무효화한다.
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.PRODUCT_DETAIL_CACHE, key = "#productId"),
            @CacheEvict(cacheNames = CacheConfig.PRODUCT_LIST_CACHE, allEntries = true)
    })
    public ProductResponse update(MemberUserDetails principal, Long productId, ProductRegisterRequest request) {
        requireSeller(principal);
        Product product = findProductWithSeller(productId);
        requireOwner(principal, product);

        product.update(request.name(), request.description(), request.basePrice(), request.maxParticipants());
        priceTierRepository.deleteByProductId(productId);
        List<PriceTier> priceTiers = savePriceTiers(product, request.priceTiers());

        log.info("상품 수정 완료: productId={}", productId);
        return ProductResponse.of(product, priceTiers);
    }

    // update와 동일한 이유로 상세(해당 productId)·목록(전체) 캐시를 함께 무효화한다.
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.PRODUCT_DETAIL_CACHE, key = "#productId"),
            @CacheEvict(cacheNames = CacheConfig.PRODUCT_LIST_CACHE, allEntries = true)
    })
    public void delete(MemberUserDetails principal, Long productId) {
        requireSeller(principal);
        Product product = findProductWithSeller(productId);
        requireOwner(principal, product);

        priceTierRepository.deleteByProductId(productId);
        productRepository.delete(product);
        log.info("상품 삭제 완료: productId={}", productId);
    }

    private Product findProductWithSeller(Long productId) {
        return productRepository.findByIdWithSeller(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    private List<PriceTier> savePriceTiers(Product product, List<PriceTierRequest> requests) {
        List<PriceTier> priceTiers = requests.stream()
                .map(tier -> new PriceTier(product, tier.minCount(), tier.price()))
                .toList();
        return priceTierRepository.saveAll(priceTiers);
    }

    private void requireSeller(MemberUserDetails principal) {
        if (principal.getMember().getRole() != Role.SELLER) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void requireOwner(MemberUserDetails principal, Product product) {
        if (!product.getSeller().getId().equals(principal.getMember().getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
