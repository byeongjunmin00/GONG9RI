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
import com.gong9ri.gong9ri.entity.ProductImage;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.entity.TeamStatus;
import com.gong9ri.gong9ri.repository.BestPriceProjection;
import com.gong9ri.gong9ri.repository.GroupBuyTeamRepository;
import com.gong9ri.gong9ri.repository.PriceTierRepository;
import com.gong9ri.gong9ri.repository.ProductImageRepository;
import com.gong9ri.gong9ri.repository.PaymentRepository;
import com.gong9ri.gong9ri.repository.WishlistRepository;
import com.gong9ri.gong9ri.repository.InquiryRepository;
import com.gong9ri.gong9ri.repository.RefundRequestRepository;
import com.gong9ri.gong9ri.repository.NotificationRepository;
import com.gong9ri.gong9ri.repository.TeamParticipationRepository;
import com.gong9ri.gong9ri.repository.SellerRevenueSummaryRepository;
import com.gong9ri.gong9ri.repository.RevenueSummaryProjection;
import com.gong9ri.gong9ri.repository.ProductRepository;
import com.gong9ri.gong9ri.repository.ProductReviewStatProjection;
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
    private final ProductImageRepository productImageRepository;
    private final GroupBuyTeamRepository groupBuyTeamRepository;
    private final ReviewRepository reviewRepository;
    // 상품 삭제 가드/정리용(product/admin)
    private final PaymentRepository paymentRepository;
    private final WishlistRepository wishlistRepository;
    private final InquiryRepository inquiryRepository;
    // 관리자 강제 삭제 전용(product/admin)
    private final RefundRequestRepository refundRequestRepository;
    private final NotificationRepository notificationRepository;
    private final TeamParticipationRepository teamParticipationRepository;
    private final SellerRevenueSummaryRepository sellerRevenueSummaryRepository;
    private final SearchTrendService searchTrendService;

    @Value("${kakao.js-key}")
    private String kakaoJsKey;

    // 공유 카드에 실을 정식 주소를 만드는 데 쓴다(share/kakao-share). 이메일 인증 링크가 쓰는 값과 동일.
    @Value("${app.base-url}")
    private String baseUrl;

    // 판매자 신뢰 배지(product/seller-trust) 기준 — 실측 근거 없는 초기값, 운영하며 조정 예정.
    // 평균 평점만 보면 리뷰 1~2개짜리 판매자도 배지를 달 수 있어 최소 리뷰 개수도 함께 요구한다.
    // 상품당 이미지 장수 상한(product/image) — 슬라이더로 넘겨 보기에 적당하고 볼륨 용량도 통제된다.
    private static final int MAX_IMAGES_PER_PRODUCT = 5;

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
        Map<Long, ProductReviewStatProjection> reviewStats = reviewStatMap(productIds);

        Page<ProductSummaryResponse> mapped = products.map(
                product -> {
                    ProductReviewStatProjection stat = reviewStats.get(product.getId());
                    Double avg = stat != null ? roundRating(stat.averageRating()) : null;
                    Integer cnt = stat != null && stat.reviewCount() != null ? stat.reviewCount().intValue() : 0;
                    return ProductSummaryResponse.of(product, bestPrices.get(product.getId()),
                            trustedSellers.getOrDefault(product.getSeller().getId(), false), avg, cnt);
                });
        return ProductPageResponse.of(mapped);
    }

    private Double roundRating(Double rating) {
        if (rating == null) {
            return null;
        }
        return Math.round(rating * 10.0) / 10.0;
    }

    private Map<Long, ProductReviewStatProjection> reviewStatMap(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return reviewRepository.findProductReviewStats(productIds).stream()
                .collect(Collectors.toMap(ProductReviewStatProjection::productId, stat -> stat));
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

    /**
     * 관리자 상품 현황 (product/admin) — <b>숨김 상품까지 전부</b> 보여준다.
     *
     * <p>공개 목록({@link #list})은 숨김을 빼기 때문에, 그걸 그대로 쓰면 숨긴 상품을 되돌릴 방법이
     * 없어진다. 캐시도 타지 않는다 — 관리자 화면은 방금 한 숨김/삭제가 즉시 반영돼야 한다.
     */
    public ProductPageResponse listForAdmin(int page, int size) {
        if (page < 0 || size < 1) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        Page<Product> products = productRepository.findAllForAdmin(PageRequest.of(page, size));
        List<Long> productIds = products.getContent().stream().map(Product::getId).toList();
        Map<Long, Integer> bestPrices = productIds.isEmpty()
                ? Map.of()
                : priceTierRepository.findBestPricesByProductIds(productIds).stream()
                        .collect(Collectors.toMap(BestPriceProjection::getProductId, BestPriceProjection::getBestPrice));

        // 관리자 화면은 신뢰배지·리뷰 통계를 안 쓴다 — 목록에 필요 없는 집계를 위해 쿼리를 더 쏘지 않는다.
        Page<ProductSummaryResponse> mapped = products.map(product ->
                ProductSummaryResponse.of(product, bestPrices.get(product.getId()), false, null, 0));
        return ProductPageResponse.of(mapped);
    }

    // 상품 상세도 등록/수정/삭제 전까지 안 변해 productId 기준으로 캐싱한다 (docs/policy/caching.md).
    @Cacheable(cacheNames = CacheConfig.PRODUCT_DETAIL_CACHE, key = "#productId")
    public ProductResponse detail(Long productId) {
        Product product = findProductWithSeller(productId);
        // 숨김 상품은 직접 링크로도 열리지 않는다 — 목록에서만 빼면 주소를 아는 사람은 계속 볼 수 있다.
        // **관리자에게도 똑같이 404다.** 이 응답은 productId만으로 캐싱되므로 요청자 역할에 따라
        // 결과가 달라지면 관리자가 조회한 값이 캐시에 남아 모두에게 나간다. 관리자는 상품 현황 목록
        // (GET /api/admin/products)에서 숨김 상태를 보고 되돌린다.
        if (product.isHidden()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        List<PriceTier> priceTiers = priceTierRepository.findByProductIdOrderByMinCountAsc(productId);
        boolean trusted = trustedSellerMap(List.of(product.getSeller().getId()))
                .getOrDefault(product.getSeller().getId(), false);
        ProductResponse baseResponse = ProductResponse.of(product, priceTiers, kakaoJsKey, trusted, baseUrl);
        ProductReviewStatProjection reviewStat = reviewStatMap(List.of(productId)).get(productId);
        Double ratingAvg = reviewStat != null ? roundRating(reviewStat.averageRating()) : null;
        Integer reviewCnt = reviewStat != null && reviewStat.reviewCount() != null ? reviewStat.reviewCount().intValue() : 0;
        // 이미지 목록은 상세에서만 채운다 — 목록 조회(list)는 상품 20개를 한 번에 내리므로 여기서
        // 이미지까지 조회하면 N+1이 된다. 카드에는 대표 이미지(product.imageUrl) 한 장이면 충분하다.
        List<String> imageUrls = productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId).stream()
                .map(ProductImage::getUrl)
                .toList();
        return baseResponse.withReviewStats(ratingAvg, reviewCnt).withImages(imageUrls);
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
        saveProductImages(saved, request.imageUrls());
        log.info("상품 등록 완료: productId={}, sellerId={}", saved.getId(), seller.getId());
        boolean trusted = trustedSellerMap(List.of(seller.getId())).getOrDefault(seller.getId(), false);
        return ProductResponse.of(saved, priceTiers, kakaoJsKey, trusted, baseUrl);
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
        productImageRepository.deleteByProductId(productId);
        saveProductImages(product, request.imageUrls());

        log.info("상품 수정 완료: productId={}", productId);
        Long sellerId = product.getSeller().getId();
        boolean trusted = trustedSellerMap(List.of(sellerId)).getOrDefault(sellerId, false);
        return ProductResponse.of(product, priceTiers, kakaoJsKey, trusted, baseUrl);
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
        deleteInternal(product, productId);
        log.info("상품 삭제 완료: productId={}", productId);
    }

    /**
     * 관리자 상품 숨김/해제 (product/admin).
     *
     * <p>삭제와 달리 <b>되돌릴 수 있고 데이터가 그대로 남는다.</b> 결제·리뷰가 달려 삭제할 수 없는
     * 상품(FK 제약)을 목록에서 치우거나, 잠깐 내렸다가 되살릴 때 쓴다.
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.PRODUCT_DETAIL_CACHE, key = "#productId"),
            @CacheEvict(cacheNames = CacheConfig.PRODUCT_LIST_CACHE, allEntries = true)
    })
    public void setHiddenByAdmin(MemberUserDetails principal, Long productId, boolean hidden) {
        requireAdminRole(principal);
        Product product = findProductWithSeller(productId);
        if (hidden) {
            product.hide();
        } else {
            product.unhide();
        }
        log.info("관리자 상품 {}: adminId={}, productId={}",
                hidden ? "숨김" : "숨김 해제", principal.getMember().getId(), productId);
    }

    private void requireAdminRole(MemberUserDetails principal) {
        if (principal.getMember().getRole() != Role.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    /**
     * 관리자 상품 삭제 (product/admin). 판매자 본인이 아니어도 지울 수 있다는 점만 다르고, 삭제 정책과
     * 캐시 무효화는 판매자 삭제와 완전히 동일하다 — 두 경로가 갈라지면 한쪽만 고쳐지는 일이 생긴다.
     *
     * <p>권한 검사는 {@code AdminService}가 하지만, 이 메서드만 따로 호출돼도 안전하도록 여기서도 확인한다.
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.PRODUCT_DETAIL_CACHE, key = "#productId"),
            @CacheEvict(cacheNames = CacheConfig.PRODUCT_LIST_CACHE, allEntries = true)
    })
    public void deleteByAdmin(MemberUserDetails principal, Long productId, boolean force) {
        requireAdminRole(principal);
        Product product = findProductWithSeller(productId);
        Long sellerId = product.getSeller().getId();

        if (force) {
            forceDeleteRelated(productId);
        }
        deleteInternal(product, productId);

        if (force) {
            // 매출 요약은 결제마다 누적(incrementPaid)만 하는 집계 테이블이라, 결제를 지워도 저절로
            // 줄지 않는다. 남은 결제 기준으로 다시 계산해 덮어쓴다 — 안 하면 판매자 수익이 실제보다
            // 부풀려진 채로 남는다.
            recomputeRevenueSummary(sellerId);
        }
        log.info("관리자 상품 삭제: adminId={}, productId={}, force={}",
                principal.getMember().getId(), productId, force);
    }

    /**
     * 강제 삭제 — 상품에 딸린 결제·리뷰·공구팀까지 전부 지운다 (product/admin).
     *
     * <p><b>순서가 곧 정확성이다.</b> 이 테이블들은 FK(NO ACTION)로 묶여 있어서, 참조하는 쪽을 먼저
     * 지우지 않으면 그 자리에서 실패한다. 실제 참조 관계는 다음과 같다(information_schema 확인):
     * <pre>
     *   refund_request → payment → product
     *   notification, team_participation, payment → group_buy_team → product
     * </pre>
     *
     * <p>일반 삭제가 막는 데이터를 일부러 지우는 경로다. 장난성 게시물 정리처럼 <b>기록을 남길 가치가
     * 없다고 관리자가 판단한 경우</b>에만 쓴다. 되돌릴 수 있는 정리는 숨김(setHiddenByAdmin)이 맞다.
     */
    private void forceDeleteRelated(Long productId) {
        refundRequestRepository.deleteByPayment_Product_Id(productId);
        paymentRepository.deleteByProduct_Id(productId);
        reviewRepository.deleteByProductId(productId);
        notificationRepository.deleteByRelatedTeam_Product_Id(productId);
        teamParticipationRepository.deleteByTeam_Product_Id(productId);
        groupBuyTeamRepository.deleteByProduct_Id(productId);
    }

    private void recomputeRevenueSummary(Long sellerId) {
        RevenueSummaryProjection recomputed = paymentRepository.findRevenueSummaryBySellerId(sellerId);
        int updated = sellerRevenueSummaryRepository.overwrite(sellerId,
                recomputed.getTotalRevenue(), recomputed.getPaidCount(), recomputed.getRefundedCount());
        if (updated > 0) {
            log.info("강제 삭제 후 매출 요약 재계산: sellerId={}, totalRevenue={}, paidCount={}",
                    sellerId, recomputed.getTotalRevenue(), recomputed.getPaidCount());
        }
    }

    /**
     * 실제 삭제. <b>돈·기록이 걸린 상품은 지우지 못하게 막는다</b> — 관리자 회원 삭제(MEMBER_HAS_ACTIVITY)와
     * 같은 정책이다.
     *
     * <p>이 가드가 없으면 결제·공구팀·리뷰가 달린 상품을 지울 때 FK 위반이 그대로 새어나가 <b>500</b>이 된다
     * (2026-08-21 관리자 삭제를 만들며 발견 — 판매자 삭제에도 원래 있던 구멍이라 같이 막았다).
     * 찜·문의는 그 상품에 종속된 데이터라 막지 않고 함께 지운다.
     */
    private void deleteInternal(Product product, Long productId) {
        if (paymentRepository.existsByProduct_Id(productId)
                || groupBuyTeamRepository.existsByProduct_Id(productId)
                || reviewRepository.existsByProductId(productId)) {
            throw new BusinessException(ErrorCode.PRODUCT_HAS_ACTIVITY);
        }

        wishlistRepository.deleteByProduct_Id(productId);
        inquiryRepository.deleteByProduct_Id(productId);
        priceTierRepository.deleteByProductId(productId);
        // 상품을 지우면 이미지 행도 함께 지운다 — 남겨두면 FK 위반으로 삭제 자체가 실패한다.
        // (볼륨의 실제 파일은 지우지 않는다 — 삭제 실패 시 파일만 사라지는 상태를 만들지 않기 위해,
        //  파일 정리는 별도 관심사로 남겨둔다. 알려진 한계로 design.md에 기록.)
        productImageRepository.deleteByProductId(productId);
        productRepository.delete(product);
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

    /**
     * 상품 이미지 목록을 저장하고 대표 이미지({@code Product.imageUrl})를 첫 장으로 맞춘다.
     *
     * <p>대표 이미지를 상품 행에 따로 들고 있는 건 의도적인 비정규화다 — 목록 조회는 상품 20개를 한 번에
     * 내리는데 이미지를 매번 조인하면 N+1이 된다({@code group_buy_team.current_count}와 같은 결).
     *
     * <p>{@code imageUrls}가 비어 있으면 아무것도 하지 않는다. 즉 이 필드를 안 보내는 기존 클라이언트는
     * 예전처럼 {@code imageUrl} 한 장만 쓰는 동작 그대로다.
     */
    private void saveProductImages(Product product, List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return;
        }
        List<String> urls = imageUrls.stream()
                .filter(url -> url != null && !url.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (urls.isEmpty()) {
            return;
        }
        // 프론트에서도 막지만 서버가 다시 강제한다 — 프론트 가드는 우회 가능하고, 장수 제한은
        // 볼륨 용량과 직결된 제약이다.
        if (urls.size() > MAX_IMAGES_PER_PRODUCT) {
            throw new BusinessException(ErrorCode.TOO_MANY_IMAGES);
        }

        List<ProductImage> images = new java.util.ArrayList<>();
        for (int order = 0; order < urls.size(); order++) {
            images.add(new ProductImage(product, urls.get(order), order));
        }
        productImageRepository.saveAll(images);
        product.changeRepresentativeImage(urls.get(0));
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
