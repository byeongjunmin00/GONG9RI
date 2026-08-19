package com.gong9ri.gong9ri.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.entity.GroupBuyTeam;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.PriceTier;
import com.gong9ri.gong9ri.entity.Product;
import com.gong9ri.gong9ri.entity.ProductCategory;
import com.gong9ri.gong9ri.entity.Review;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.repository.GroupBuyTeamRepository;
import com.gong9ri.gong9ri.repository.MemberRepository;
import com.gong9ri.gong9ri.repository.PriceTierRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import com.gong9ri.gong9ri.repository.ReviewRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PriceTierRepository priceTierRepository;

    @Autowired
    private GroupBuyTeamRepository groupBuyTeamRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private Member saveMember(String username, Role role) {
        Member member = new Member(username, "encoded-password", "테스트유저", username + "@test.com", role);
        return memberRepository.save(member);
    }

    private RequestPostProcessor asUser(Member member) {
        MemberUserDetails principal = new MemberUserDetails(member);
        UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        return authentication(token);
    }

    private Product saveProduct(Member seller) {
        Product product = new Product(seller, "제주 감귤 5kg", "직접 재배한 감귤", 25000, 10, null);
        Product saved = productRepository.save(product);
        priceTierRepository.save(new PriceTier(saved, 2, 22000));
        priceTierRepository.save(new PriceTier(saved, 10, 15000));
        return saved;
    }

    private Product saveProduct(Member seller, String name, ProductCategory category) {
        Product product = new Product(seller, name, "설명", 10000, 10, null, false, category);
        return productRepository.save(product);
    }

    private Map<String, Object> registerRequestBody() {
        return Map.of(
                "name", "제주 감귤 5kg",
                "description", "직접 재배한 감귤",
                "basePrice", 25000,
                "maxParticipants", 10,
                "priceTiers", List.of(
                        Map.of("minCount", 2, "price", 22000),
                        Map.of("minCount", 10, "price", 15000)
                ),
                "category", "FOOD"
        );
    }

    @Test
    @DisplayName("상품 목록은 비로그인으로 조회 가능하고 bestPrice가 포함된다")
    void list_publicAccess() throws Exception {
        Member seller = saveMember("seller1", Role.SELLER);
        saveProduct(seller);

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].bestPrice").value(15000))
                .andExpect(jsonPath("$.data.content[0].sellerName").value("테스트유저"));
    }

    @Test
    @DisplayName("상품 상세는 비로그인으로 조회 가능하고 priceTiers 전체를 반환한다")
    void detail_publicAccess() throws Exception {
        Member seller = saveMember("seller2", Role.SELLER);
        Product product = saveProduct(seller);

        mockMvc.perform(get("/api/products/" + product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.priceTiers.length()").value(2))
                .andExpect(jsonPath("$.data.sellerId").value(seller.getId().intValue()))
                .andExpect(jsonPath("$.data.kakaoJsKey").value("dummy-test-kakao-js-key"));
    }

    @Test
    @DisplayName("존재하지 않는 상품 조회 시 404 PRODUCT_NOT_FOUND")
    void detail_notFound() throws Exception {
        mockMvc.perform(get("/api/products/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    @DisplayName("판매자가 로그인 상태로 상품을 등록하면 201")
    void register_success() throws Exception {
        Member seller = saveMember("seller3", Role.SELLER);

        mockMvc.perform(post("/api/products")
                        .with(asUser(seller))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(registerRequestBody())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("제주 감귤 5kg"))
                .andExpect(jsonPath("$.data.priceTiers.length()").value(2));
    }

    @Test
    @DisplayName("구매자 계정으로 상품 등록 시 403 FORBIDDEN")
    void register_forbidden_buyer() throws Exception {
        Member buyer = saveMember("buyer1", Role.BUYER);

        mockMvc.perform(post("/api/products")
                        .with(asUser(buyer))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(registerRequestBody())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("비로그인으로 상품 등록 시 401 UNAUTHORIZED(공통 응답 형식)")
    void register_unauthorized() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(registerRequestBody())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("필수값이 비어있으면 400 VALIDATION_FAILED")
    void register_validationFailed() throws Exception {
        Member seller = saveMember("seller4", Role.SELLER);
        Map<String, Object> invalid = Map.of(
                "name", "",
                "basePrice", 25000,
                "maxParticipants", 10,
                "priceTiers", List.of(Map.of("minCount", 2, "price", 22000))
        );

        mockMvc.perform(post("/api/products")
                        .with(asUser(seller))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("카테고리 없이 등록하면 400 VALIDATION_FAILED")
    void register_missingCategory_validationFailed() throws Exception {
        Member seller = saveMember("seller12", Role.SELLER);
        Map<String, Object> invalid = Map.of(
                "name", "제주 감귤 5kg",
                "basePrice", 25000,
                "maxParticipants", 10,
                "priceTiers", List.of(Map.of("minCount", 2, "price", 22000))
        );

        mockMvc.perform(post("/api/products")
                        .with(asUser(seller))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("priceTiers의 minCount가 2 미만이면 400 VALIDATION_FAILED")
    void register_minCountBelowTwo_validationFailed() throws Exception {
        Member seller = saveMember("seller24", Role.SELLER);
        Map<String, Object> invalid = Map.of(
                "name", "제주 감귤 5kg",
                "basePrice", 25000,
                "maxParticipants", 10,
                "priceTiers", List.of(Map.of("minCount", 1, "price", 22000)),
                "category", "FOOD"
        );

        mockMvc.perform(post("/api/products")
                        .with(asUser(seller))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("openAt에 미래 시각을 넣어 등록하면 201이고 응답에 그대로 반영된다")
    void register_withFutureOpenAt_success() throws Exception {
        Member seller = saveMember("seller18", Role.SELLER);
        String futureOpenAt = LocalDateTime.now().plusDays(3).withNano(0).toString();
        Map<String, Object> body = Map.of(
                "name", "오픈예정테스트상품",
                "basePrice", 10000,
                "maxParticipants", 5,
                "priceTiers", List.of(Map.of("minCount", 2, "price", 9000)),
                "category", "ETC",
                "openAt", futureOpenAt
        );

        mockMvc.perform(post("/api/products")
                        .with(asUser(seller))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.openAt").value(futureOpenAt));
    }

    @Test
    @DisplayName("openAt에 과거 시각을 넣어 등록하면 400 VALIDATION_FAILED")
    void register_withPastOpenAt_validationFailed() throws Exception {
        Member seller = saveMember("seller19", Role.SELLER);
        Map<String, Object> body = Map.of(
                "name", "잘못된오픈예정상품",
                "basePrice", 10000,
                "maxParticipants", 5,
                "priceTiers", List.of(Map.of("minCount", 2, "price", 9000)),
                "category", "ETC",
                "openAt", LocalDateTime.now().minusDays(1).toString()
        );

        mockMvc.perform(post("/api/products")
                        .with(asUser(seller))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("category 쿼리파라미터로 목록을 필터링하면 그 카테고리 상품만 반환된다")
    void list_filterByCategory_returnsOnlyMatchingCategory() throws Exception {
        Member seller = saveMember("seller13", Role.SELLER);
        saveProduct(seller, "식품상품", ProductCategory.FOOD);
        saveProduct(seller, "뷰티상품", ProductCategory.BEAUTY);

        mockMvc.perform(get("/api/products").param("category", "FOOD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("식품상품"))
                .andExpect(jsonPath("$.data.content[0].category").value("FOOD"));
    }

    @Test
    @DisplayName("keyword로 상품명 또는 판매자명에 포함된 상품만 검색된다")
    void list_searchByKeyword_matchesProductNameOrSellerName() throws Exception {
        // saveMember()는 name을 항상 "테스트유저"로 고정하므로(username과 무관), 판매자명 검색을
        // 검증하려면 name을 직접 지정해서 저장한다.
        Member seller = memberRepository.save(
                new Member("sellerMelon", "encoded-password", "멜론농장", "sellerMelon@test.com", Role.SELLER));
        saveProduct(seller, "제주 감귤 세트", ProductCategory.FOOD);
        saveProduct(seller, "완전 무관한 상품", ProductCategory.FOOD);

        mockMvc.perform(get("/api/products").param("keyword", "감귤"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("제주 감귤 세트"));

        // 판매자명으로도 검색된다.
        mockMvc.perform(get("/api/products").param("keyword", "멜론농장"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2));
    }

    @Test
    @DisplayName("keyword가 있으면 목록 캐시를 타지 않는다 — 같은 검색어로 연속 조회해도 새로 등록된 상품이 즉시 반영된다")
    void list_searchByKeyword_bypassesCache() throws Exception {
        Member seller = saveMember("seller16", Role.SELLER);
        saveProduct(seller, "캐시테스트상품1", ProductCategory.ETC);

        mockMvc.perform(get("/api/products").param("keyword", "캐시테스트"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1));

        // 같은 page/size/keyword 조합으로 다시 요청 — 목록 캐시를 탔다면(회귀) 방금 추가한 상품이
        // 이 두 번째 응답에 반영되지 않아야 하는데, 캐시를 안 타므로 실제로 반영돼야 한다.
        saveProduct(seller, "캐시테스트상품2", ProductCategory.ETC);

        mockMvc.perform(get("/api/products").param("keyword", "캐시테스트"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2));
    }

    @Test
    @DisplayName("목록의 진행바 정보는 RECRUITING 팀 중 진행률이 가장 높은 팀을 대표로 보여주고, "
            + "목록 캐시가 이미 채워져 있어도 팀 상태 변화를 즉시 반영한다(캐시하지 않음)")
    void list_activeTeamProgress_showsHighestRatioTeam_andReflectsLiveTeamChanges() throws Exception {
        int size = 201; // 다른 테스트와 캐시 키(page+size+category)가 겹치지 않게 이 테스트 전용 size 사용
        Member seller = saveMember("seller14", Role.SELLER);
        Member leaderLowRatio = saveMember("leaderLowRatio", Role.BUYER);
        Member leaderHighRatio = saveMember("leaderHighRatio", Role.BUYER);
        Product product = saveProduct(seller, "진행바테스트상품", ProductCategory.FASHION);

        // 진행률 0.2(1/5) 팀과 0.05(1/20) 팀을 동시에 만든다 — 더 높은 쪽(5명 목표 팀)이 대표로 뽑혀야 한다.
        GroupBuyTeam highRatioTeam = groupBuyTeamRepository.save(
                new GroupBuyTeam(product, leaderLowRatio, 5, LocalDateTime.now().plusDays(1)));
        groupBuyTeamRepository.save(new GroupBuyTeam(product, leaderHighRatio, 20, LocalDateTime.now().plusDays(1)));

        mockMvc.perform(get("/api/products").param("category", "FASHION").param("size", String.valueOf(size)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].activeTeamCurrentCount").value(1))
                .andExpect(jsonPath("$.data.content[0].activeTeamTargetParticipants").value(5));

        // 목록 캐시(page+size+category 키)는 이미 위 호출로 채워졌다. 그 캐시를 갱신하는 경로(register/update
        // /delete)를 거치지 않고 팀 참가 결과만 직접 반영해, 진행바가 캐시된 스냅샷이 아니라 항상 최신
        // 팀 상태를 조회한다는 걸 증명한다.
        highRatioTeam.increaseParticipant();
        groupBuyTeamRepository.save(highRatioTeam);

        mockMvc.perform(get("/api/products").param("category", "FASHION").param("size", String.valueOf(size)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].activeTeamCurrentCount").value(2));
    }

    @Test
    @DisplayName("sort=POPULAR이면 RECRUITING 팀 중 참여 인원이 가장 많은 팀 기준으로 상품이 내림차순 정렬된다")
    void list_sortPopular_ordersByHighestActiveTeamCount() throws Exception {
        int size = 202; // 다른 테스트와 캐시 키가 겹치지 않게 이 테스트 전용 size 사용
        Member seller = saveMember("seller15", Role.SELLER);
        Member leaderA = saveMember("leaderA", Role.BUYER);
        Member leaderB = saveMember("leaderB", Role.BUYER);
        Product lessPopular = saveProduct(seller, "인기순테스트-비인기", ProductCategory.LIVING);
        Product morePopular = saveProduct(seller, "인기순테스트-인기", ProductCategory.LIVING);

        groupBuyTeamRepository.save(new GroupBuyTeam(lessPopular, leaderA, 20, LocalDateTime.now().plusDays(1)));
        GroupBuyTeam popularTeam =
                groupBuyTeamRepository.save(new GroupBuyTeam(morePopular, leaderB, 5, LocalDateTime.now().plusDays(1)));
        popularTeam.increaseParticipant();
        popularTeam.increaseParticipant();
        groupBuyTeamRepository.save(popularTeam); // currentCount=3, lessPopular 쪽은 1명뿐

        mockMvc.perform(get("/api/products")
                        .param("category", "LIVING")
                        .param("size", String.valueOf(size))
                        .param("sort", "POPULAR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("인기순테스트-인기"))
                .andExpect(jsonPath("$.data.content[1].name").value("인기순테스트-비인기"));
    }

    @Test
    @DisplayName("sort=DEADLINE이면 RECRUITING 팀 중 가장 이른 마감일 기준으로 오름차순 정렬되고, "
            + "진행 중인 팀이 없는 상품은 맨 뒤로 밀린다")
    void list_sortDeadline_ordersByNearestDeadline_andPushesNoTeamProductsLast() throws Exception {
        int size = 203; // 다른 테스트와 캐시 키가 겹치지 않게 이 테스트 전용 size 사용
        Member seller = saveMember("seller17", Role.SELLER);
        Member leaderA = saveMember("leaderDeadlineA", Role.BUYER);
        Member leaderB = saveMember("leaderDeadlineB", Role.BUYER);
        Product noTeamProduct = saveProduct(seller, "마감임박테스트-팀없음", ProductCategory.DIGITAL);
        Product soonProduct = saveProduct(seller, "마감임박테스트-임박", ProductCategory.DIGITAL);
        Product laterProduct = saveProduct(seller, "마감임박테스트-여유", ProductCategory.DIGITAL);

        groupBuyTeamRepository.save(new GroupBuyTeam(laterProduct, leaderB, 5, LocalDateTime.now().plusDays(10)));
        groupBuyTeamRepository.save(new GroupBuyTeam(soonProduct, leaderA, 5, LocalDateTime.now().plusDays(1)));

        mockMvc.perform(get("/api/products")
                        .param("category", "DIGITAL")
                        .param("size", String.valueOf(size))
                        .param("sort", "DEADLINE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("마감임박테스트-임박"))
                .andExpect(jsonPath("$.data.content[1].name").value("마감임박테스트-여유"))
                .andExpect(jsonPath("$.data.content[2].name").value("마감임박테스트-팀없음"));
    }

    @Test
    @DisplayName("본인 상품 수정 시 200")
    void update_success_owner() throws Exception {
        Member seller = saveMember("seller5", Role.SELLER);
        Product product = saveProduct(seller);

        Map<String, Object> updateBody = Map.of(
                "name", "수정된 이름",
                "description", "수정된 설명",
                "basePrice", 30000,
                "maxParticipants", 8,
                "priceTiers", List.of(Map.of("minCount", 2, "price", 20000)),
                "category", "LIVING"
        );

        mockMvc.perform(put("/api/products/" + product.getId())
                        .with(asUser(seller))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("수정된 이름"))
                .andExpect(jsonPath("$.data.priceTiers.length()").value(1));
    }

    @Test
    @DisplayName("타인 상품 수정 시 403 FORBIDDEN")
    void update_forbidden_notOwner() throws Exception {
        Member seller = saveMember("seller6", Role.SELLER);
        Product product = saveProduct(seller);
        Member otherSeller = saveMember("seller7", Role.SELLER);

        mockMvc.perform(put("/api/products/" + product.getId())
                        .with(asUser(otherSeller))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(registerRequestBody())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("존재하지 않는 상품 수정 시 404 PRODUCT_NOT_FOUND")
    void update_notFound() throws Exception {
        Member seller = saveMember("seller8", Role.SELLER);

        mockMvc.perform(put("/api/products/999999")
                        .with(asUser(seller))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(registerRequestBody())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    @DisplayName("본인 상품 삭제 시 204")
    void delete_success_owner() throws Exception {
        Member seller = saveMember("seller9", Role.SELLER);
        Product product = saveProduct(seller);

        mockMvc.perform(delete("/api/products/" + product.getId())
                        .with(asUser(seller)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("타인 상품 삭제 시 403 FORBIDDEN")
    void delete_forbidden_notOwner() throws Exception {
        Member seller = saveMember("seller10", Role.SELLER);
        Product product = saveProduct(seller);
        Member otherSeller = saveMember("seller11", Role.SELLER);

        mockMvc.perform(delete("/api/products/" + product.getId())
                        .with(asUser(otherSeller)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("판매자 평균 평점 4.5 이상·리뷰 3개 이상이면 상품 상세에 sellerTrustedBadge=true")
    void detail_sellerTrustedBadge_true_whenRatingAndCountMeetThreshold() throws Exception {
        Member seller = saveMember("seller20", Role.SELLER);
        Product product = saveProduct(seller);
        for (int i = 0; i < 3; i++) {
            Member buyer = saveMember("trustedReviewer" + i, Role.BUYER);
            reviewRepository.save(new Review(product, buyer, 5, "좋아요"));
        }

        mockMvc.perform(get("/api/products/" + product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sellerTrustedBadge").value(true));
    }

    @Test
    @DisplayName("리뷰가 3개 미만이면 평점이 만점이어도 sellerTrustedBadge=false")
    void detail_sellerTrustedBadge_false_whenReviewCountBelowThreshold() throws Exception {
        Member seller = saveMember("seller21", Role.SELLER);
        Product product = saveProduct(seller);
        for (int i = 0; i < 2; i++) {
            Member buyer = saveMember("underReviewer" + i, Role.BUYER);
            reviewRepository.save(new Review(product, buyer, 5, "좋아요"));
        }

        mockMvc.perform(get("/api/products/" + product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sellerTrustedBadge").value(false));
    }

    @Test
    @DisplayName("리뷰가 하나도 없으면 sellerTrustedBadge=false")
    void detail_sellerTrustedBadge_false_whenNoReviews() throws Exception {
        Member seller = saveMember("seller22", Role.SELLER);
        Product product = saveProduct(seller);

        mockMvc.perform(get("/api/products/" + product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sellerTrustedBadge").value(false));
    }

    @Test
    @DisplayName("목록 조회 응답에도 sellerTrustedBadge가 포함된다")
    void list_includesSellerTrustedBadge() throws Exception {
        int size = 204; // 다른 테스트와 캐시 키가 겹치지 않게 이 테스트 전용 size 사용
        Member seller = saveMember("seller23", Role.SELLER);
        Product product = saveProduct(seller, "신뢰배지목록테스트상품", ProductCategory.ETC);
        for (int i = 0; i < 3; i++) {
            Member buyer = saveMember("listTrustReviewer" + i, Role.BUYER);
            reviewRepository.save(new Review(product, buyer, 5, "좋아요"));
        }

        mockMvc.perform(get("/api/products").param("category", "ETC").param("size", String.valueOf(size)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].sellerTrustedBadge").value(true));
    }

    @Test
    @DisplayName("keyword로 검색하면 실시간 인기 검색어 집계에 반영되고, 해당 엔드포인트에서 조회된다")
    void list_withKeyword_recordsSearchTrend_andSearchTrendsEndpointReturnsIt() throws Exception {
        // /api/products/{productId}가 아니라 /api/products/search-trends(리터럴 경로)가 우선 매칭되는지도
        // 같이 확인한다 — Long 파싱 실패로 400이 나면 라우팅 우선순위가 깨진 것.
        String keyword = "검색어트렌드테스트" + System.nanoTime();
        try {
            mockMvc.perform(get("/api/products").param("keyword", keyword))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/products/search-trends").param("limit", "50"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.keywords", hasItem(keyword)));
        } finally {
            String todayKey = "search-trend:" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            redisTemplate.opsForZSet().remove(todayKey, keyword);
        }
    }
}
