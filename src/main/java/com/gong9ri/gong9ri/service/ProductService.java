package com.gong9ri.gong9ri.service;

import com.gong9ri.gong9ri.common.exception.BusinessException;
import com.gong9ri.gong9ri.common.exception.ErrorCode;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.config.CacheConfig;
import com.gong9ri.gong9ri.dto.PriceTierRequest;
import com.gong9ri.gong9ri.dto.ProductPageResponse;
import com.gong9ri.gong9ri.dto.ProductRegisterRequest;
import com.gong9ri.gong9ri.dto.ProductResponse;
import com.gong9ri.gong9ri.dto.ProductSort;
import com.gong9ri.gong9ri.dto.ProductSummaryResponse;
import com.gong9ri.gong9ri.entity.GroupBuyTeam;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.PriceTier;
import com.gong9ri.gong9ri.entity.Product;
import com.gong9ri.gong9ri.entity.ProductCategory;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.entity.TeamStatus;
import com.gong9ri.gong9ri.repository.BestPriceProjection;
import com.gong9ri.gong9ri.repository.GroupBuyTeamRepository;
import com.gong9ri.gong9ri.repository.PriceTierRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import com.gong9ri.gong9ri.repository.ReviewRepository;
import com.gong9ri.gong9ri.repository.SellerRatingProjection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    private final GroupBuyTeamRepository groupBuyTeamRepository;
    private final ReviewRepository reviewRepository;
    private final SearchTrendService searchTrendService;

    @Value("${kakao.js-key}")
    private String kakaoJsKey;

    // 판매자 신뢰 배지(product/seller-trust) 기준 — 실측 근거 없는 초기값, 운영하며 조정 예정.
    // 평균 평점만 보면 리뷰 1~2개짜리 판매자도 배지를 달 수 있어 최소 리뷰 개수도 함께 요구한다.
    private static final double TRUSTED_SELLER_MIN_RATING = 4.5;
    private static final long TRUSTED_SELLER_MIN_REVIEW_COUNT = 3L;

    // 조회 빈도가 높고 등록/수정/삭제 전까지 안 변해 캐싱 효과가 크다 (docs/policy/caching.md).
    // 정렬 조건(ORDER BY)이 없어 새 상품이 어느 페이지에 들어갈지 특정할 수 없으므로,
    // 무효화는 특정 키가 아니라 이 캐시 전체를 대상으로 한다(register/update/delete).
    // 캐시 키에 category를 포함해야 한다 — 안 그러면 카테고리로 필터링된 결과가 "전체" 조회 캐시를
    // 덮어써버리거나(반대의 경우도 마찬가지) 서로 다른 카테고리 결과가 같은 캐시 엔트리를 공유하게 된다.
    // sort=POPULAR도 캐시 키에 포함한다 — 인기순 순위는 팀 참가마다 바뀌는 값이라 이 페이지 자체가
    // 최대 TTL(30분)만큼 낡을 수 있는데, activeTeamCurrentCount(카드 진행바 숫자, 사실을 보여줌)와
    // 달리 "정렬 순서"는 30분 단위로 갱신돼도 되는 수준의 신선도로 판단해 그대로 캐싱한다(순위 사이트
    // 다수가 실시간이 아니라 주기 갱신인 것과 같은 이유) — activeTeamCurrentCount처럼 캐시 밖으로
    // 빼지 않는다.
    // openSoon(오픈예정 탭, product/list-enhancements)도 캐시 키에 포함한다 — 오픈예정 탭 자체가
    // 카테고리 탭들과는 별도의 결과 집합이라 키에 없으면 캐시가 서로 섞인다. 반면 "카테고리 탭에서
    // 오픈예정 상품을 제외하는 것"은 새 캐시 축이 아니다 — category 값 자체가 이미 키에 있고, 그
    // 값에 대응하는 쿼리 조건(오픈예정 제외 여부)은 항상 category 유무에 종속적으로 결정되므로 같은
    // (page, size, category, sort, openSoon) 조합은 항상 같은 SQL 조건으로 귀결된다
    // (docs/dev/ongoing/product-open-soon-tab.md "캐시 키" 절 참고).
    // keyword(product/list-search)가 있으면 아예 캐싱하지 않는다(condition) — 검색어는 조합이 사실상
    // 무한해서 캐시 키를 넣으면 대부분 한 번 쓰고 버려지는 엔트리로 캐시가 계속 불어난다(챗봇 상품검색
    // Tool의 findTop10ByNameContainingIgnoreCase도 같은 이유로 캐싱 안 함, ProductAiController 참고).
    @Cacheable(cacheNames = CacheConfig.PRODUCT_LIST_CACHE,
            condition = "#keyword == null || #keyword.isBlank()",
            key = "#page + '-' + #size + '-' + (#category != null ? #category : 'ALL')"
                    + " + '-' + (#sort != null ? #sort : 'NONE') + '-' + #openSoon")
    public ProductPageResponse list(int page, int size, ProductCategory category, ProductSort sort, String keyword,
            boolean openSoon) {
        // 실시간 인기 검색어(product/search-trends) 집계 — 이 메서드는 keyword가 있으면 캐시를 타지
        // 않아(@Cacheable condition) 실제 검색마다 항상 실행되므로, 집계 누락 없이 여기서 기록한다.
        searchTrendService.recordSearch(keyword);

        Pageable pageable = PageRequest.of(page, size);
        Page<Product> products = productRepository.findAllWithSeller(pageable, category, sort, keyword, openSoon);

        List<Long> productIds = products.getContent().stream().map(Product::getId).toList();
        Map<Long, Integer> bestPrices = productIds.isEmpty()
                ? Map.of()
                : priceTierRepository.findBestPricesByProductIds(productIds).stream()
                        .collect(Collectors.toMap(BestPriceProjection::getProductId, BestPriceProjection::getBestPrice));

        List<Long> sellerIds = products.getContent().stream().map(product -> product.getSeller().getId()).distinct().toList();
        Map<Long, Boolean> trustedSellers = trustedSellerMap(sellerIds);

        Page<ProductSummaryResponse> mapped = products.map(
                product -> ProductSummaryResponse.of(product, bestPrices.get(product.getId()),
                        trustedSellers.getOrDefault(product.getSeller().getId(), false)));
        return ProductPageResponse.of(mapped);
    }

    // 판매자 신뢰 배지(product/seller-trust) — 여러 상품의 판매자 신뢰 여부를 한 번의 집계 쿼리로 계산한다
    // (product/list-progress의 bestPrices와 동일한 N+1 회피 패턴). 목록/상세 캐시(PRODUCT_LIST_CACHE·
    // PRODUCT_DETAIL_CACHE) 안에 그대로 포함시켜 최대 TTL(30분)만큼 낡을 수 있음을 감수한다 — 리뷰 평균은
    // 팀 진행률처럼 사용자가 실시간으로 지켜보는 값이 아니라 판단 기준용 참고 지표라 activeTeamCurrentCount
    // 만큼 신선할 필요는 없다고 판단(POPULAR 정렬과 같은 이유).
    private Map<Long, Boolean> trustedSellerMap(List<Long> sellerIds) {
        if (sellerIds.isEmpty()) {
            return Map.of();
        }
        return reviewRepository.findSellerRatingSummaries(sellerIds).stream()
                .collect(Collectors.toMap(SellerRatingProjection::getSellerId, this::isTrustedSeller));
    }

    private boolean isTrustedSeller(SellerRatingProjection rating) {
        return rating.getAverageRating() != null
                && rating.getAverageRating() >= TRUSTED_SELLER_MIN_RATING
                && rating.getReviewCount() >= TRUSTED_SELLER_MIN_REVIEW_COUNT;
    }

    /**
     * 메인 페이지 카드 진행바(product/list-progress) — {@link #list}가 반환한(캐시됐을 수 있는) 페이지를
     * 받아, 각 상품의 RECRUITING 팀 중 진행률(currentCount/maxParticipants)이 가장 높은 팀의 스냅샷을
     * 얹어 새 응답을 만든다. 팀 상태는 자주 바뀌는 값이라 캐시하지 않고 항상 이 메서드를 호출한 시점의
     * 최신 값을 조회한다({@link ProductSummaryResponse} 필드 주석 참고) — {@code list}와 별도 public
     * 메서드로 분리한 이유도, 같은 클래스 안에서 {@code list}를 호출하면(self-invocation) Spring의
     * {@code @Cacheable} 프록시를 우회해버리는 문제를 피하기 위함이다(호출자가 두 메서드를 각자 호출).
     */
    public ProductPageResponse attachActiveTeamProgress(ProductPageResponse page) {
        List<Long> productIds = page.content().stream().map(ProductSummaryResponse::productId).toList();
        if (productIds.isEmpty()) {
            return page;
        }

        List<GroupBuyTeam> recruitingTeams =
                groupBuyTeamRepository.findByProductIdInAndStatus(productIds, TeamStatus.RECRUITING);

        Map<Long, GroupBuyTeam> bestTeamByProductId = recruitingTeams.stream()
                .collect(Collectors.toMap(
                        team -> team.getProduct().getId(),
                        team -> team,
                        (teamA, teamB) -> progressRatio(teamA) >= progressRatio(teamB) ? teamA : teamB));

        List<ProductSummaryResponse> enriched = page.content().stream()
                .map(summary -> {
                    GroupBuyTeam bestTeam = bestTeamByProductId.get(summary.productId());
                    return bestTeam == null
                            ? summary
                            : summary.withActiveTeamProgress(bestTeam.getCurrentCount(), bestTeam.getMaxParticipants(),
                                    bestTeam.getDeadline());
                })
                .toList();

        return new ProductPageResponse(enriched, page.page(), page.size(), page.totalElements());
    }

    private double progressRatio(GroupBuyTeam team) {
        return team.getMaxParticipants() == 0 ? 0 : (double) team.getCurrentCount() / team.getMaxParticipants();
    }

    // 상품 상세도 등록/수정/삭제 전까지 안 변해 productId 기준으로 캐싱한다 (docs/policy/caching.md).
    @Cacheable(cacheNames = CacheConfig.PRODUCT_DETAIL_CACHE, key = "#productId")
    public ProductResponse detail(Long productId) {
        Product product = findProductWithSeller(productId);
        List<PriceTier> priceTiers = priceTierRepository.findByProductIdOrderByMinCountAsc(productId);
        boolean trusted = trustedSellerMap(List.of(product.getSeller().getId()))
                .getOrDefault(product.getSeller().getId(), false);
        return ProductResponse.of(product, priceTiers, kakaoJsKey, trusted);
    }

    // 신규 상품이 어느 페이지에 들어갈지 특정할 수 없어(ORDER BY 없음) 목록 캐시를 전체 무효화한다.
    // 신규 productId는 캐시에 아직 없어 첫 조회가 자연히 미스이므로 상세 캐시는 건드릴 필요 없다.
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.PRODUCT_LIST_CACHE, allEntries = true)
    public ProductResponse register(MemberUserDetails principal, ProductRegisterRequest request) {
        requireSeller(principal);
        validateProductRegisterRequest(request);
        Member seller = principal.getMember();

        int maxParticipants = calculateMaxParticipants(request.priceTiers());

        Product product = new Product(seller, request.name(), request.description(),
                request.basePrice(), maxParticipants, request.imageUrl(),
                Boolean.TRUE.equals(request.autoRefundOnCancel()), request.category(), request.openAt());
        Product saved = productRepository.save(product);

        List<PriceTier> priceTiers = savePriceTiers(saved, request.priceTiers());
        log.info("상품 등록 완료: productId={}, sellerId={}", saved.getId(), seller.getId());
        boolean trusted = trustedSellerMap(List.of(seller.getId())).getOrDefault(seller.getId(), false);
        return ProductResponse.of(saved, priceTiers, kakaoJsKey, trusted);
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
        validateProductRegisterRequest(request);
        Product product = findProductWithSeller(productId);
        requireOwner(principal, product);

        int maxParticipants = calculateMaxParticipants(request.priceTiers());

        product.update(request.name(), request.description(), request.basePrice(), maxParticipants,
                request.imageUrl(), Boolean.TRUE.equals(request.autoRefundOnCancel()), request.category(),
                request.openAt());
        priceTierRepository.deleteByProductId(productId);
        List<PriceTier> priceTiers = savePriceTiers(product, request.priceTiers());

        log.info("상품 수정 완료: productId={}", productId);
        Long sellerId = product.getSeller().getId();
        boolean trusted = trustedSellerMap(List.of(sellerId)).getOrDefault(sellerId, false);
        return ProductResponse.of(product, priceTiers, kakaoJsKey, trusted);
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

    private int calculateMaxParticipants(List<PriceTierRequest> requests) {
        return requests.stream()
                .map(PriceTierRequest::minCount)
                .max(Integer::compareTo)
                .orElse(2);
    }

    private void validateProductRegisterRequest(ProductRegisterRequest request) {
        if (request.basePrice() == null || request.basePrice() < 1) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        if (request.priceTiers() == null || request.priceTiers().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        List<PriceTierRequest> sortedTiers = request.priceTiers().stream()
                .sorted((a, b) -> Integer.compare(a.minCount(), b.minCount()))
                .toList();

        Integer previousMinCount = null;
        Integer previousPrice = null;

        for (PriceTierRequest tier : sortedTiers) {
            if (tier.minCount() == null || tier.minCount() < 2) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED);
            }
            if (tier.price() == null || tier.price() < 1) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED);
            }

            if (tier.minCount().equals(previousMinCount)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED);
            }

            if (previousPrice == null) {
                if (tier.price() >= request.basePrice()) {
                    throw new BusinessException(ErrorCode.VALIDATION_FAILED);
                }
            } else {
                if (tier.price() > previousPrice) {
                    throw new BusinessException(ErrorCode.VALIDATION_FAILED);
                }
            }

            previousMinCount = tier.minCount();
            previousPrice = tier.price();
        }
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
