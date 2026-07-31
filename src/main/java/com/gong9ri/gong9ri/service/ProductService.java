package com.gong9ri.gong9ri.service;

import com.gong9ri.gong9ri.common.exception.BusinessException;
import com.gong9ri.gong9ri.common.exception.ErrorCode;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
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

    public ProductResponse detail(Long productId) {
        Product product = findProductWithSeller(productId);
        List<PriceTier> priceTiers = priceTierRepository.findByProductIdOrderByMinCountAsc(productId);
        return ProductResponse.of(product, priceTiers);
    }

    @Transactional
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

    @Transactional
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

    @Transactional
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
