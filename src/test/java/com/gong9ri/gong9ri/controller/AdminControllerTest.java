package com.gong9ri.gong9ri.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.entity.ChatSession;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Payment;
import com.gong9ri.gong9ri.entity.Product;
import com.gong9ri.gong9ri.entity.ProductCategory;
import com.gong9ri.gong9ri.entity.RefundRequest;
import com.gong9ri.gong9ri.entity.Review;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.repository.ChatSessionRepository;
import com.gong9ri.gong9ri.repository.MemberRepository;
import com.gong9ri.gong9ri.repository.PaymentRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import com.gong9ri.gong9ri.repository.RefundRequestRepository;
import com.gong9ri.gong9ri.repository.ReviewRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private RefundRequestRepository refundRequestRepository;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    private Member saveMember(String username, Role role) {
        Member member = new Member(username, "encoded-password", "테스트유저", username + "@test.com", role);
        return memberRepository.save(member);
    }

    private Product saveProduct(Member seller) {
        Product product = new Product(seller, "관리자테스트상품", "설명", 10000, 10, null, false, ProductCategory.ETC);
        return productRepository.save(product);
    }

    private RequestPostProcessor asUser(Member member) {
        MemberUserDetails principal = new MemberUserDetails(member);
        UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        return authentication(token);
    }

    @Test
    @DisplayName("비로그인으로 관리자 API 호출 시 401")
    void members_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/members"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("관리자가 아닌 로그인 사용자(구매자)가 관리자 API 호출 시 403 FORBIDDEN")
    void members_nonAdmin_returnsForbidden() throws Exception {
        Member buyer = saveMember("admin-test-buyer1", Role.BUYER);

        mockMvc.perform(get("/api/admin/members").with(asUser(buyer)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("관리자가 회원 목록을 조회하면 200과 페이지 응답을 반환한다")
    void members_asAdmin_returnsPage() throws Exception {
        Member admin = saveMember("admin-test-1", Role.ADMIN);
        saveMember("admin-test-buyer2", Role.BUYER);

        mockMvc.perform(get("/api/admin/members").with(asUser(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    }

    @Test
    @DisplayName("관리자가 회원 목록에 search 및 role 파라미터를 넘기면 조건에 맞는 회원만 반환한다")
    void members_withSearchAndFilter_returnsFilteredMembers() throws Exception {
        Member admin = saveMember("admin-search-admin1", Role.ADMIN);
        Member targetBuyer = saveMember("unique-search-buyer", Role.BUYER);
        saveMember("unique-search-seller", Role.SELLER);

        mockMvc.perform(get("/api/admin/members")
                        .param("search", "unique-search-buyer")
                        .param("role", "BUYER")
                        .with(asUser(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].username").value("unique-search-buyer"));
    }

    @Test
    @DisplayName("관리자가 상품 현황 목록에 search 키워드를 넘기면 매칭되는 상품만 반환한다")
    void products_withSearchAndFilter_returnsFilteredProducts() throws Exception {
        Member admin = saveMember("admin-search-admin2", Role.ADMIN);
        Member seller = saveMember("admin-search-seller2", Role.SELLER);
        Product targetProduct = new Product(seller, "관리자특별검색상품", "설명", 10000, 10, null, false, ProductCategory.ETC);
        Product otherProduct = new Product(seller, "다른일반상품", "설명", 10000, 10, null, false, ProductCategory.ETC);
        productRepository.save(targetProduct);
        productRepository.save(otherProduct);

        mockMvc.perform(get("/api/admin/products")
                        .param("search", "특별검색")
                        .param("status", "VISIBLE")
                        .with(asUser(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("관리자특별검색상품"));
    }

    @Test
    @DisplayName("관리자가 status=HIDDEN 파라미터를 넘기면 숨김 상품만 필터링되어 반환된다")
    void products_withHiddenStatusFilter_returnsOnlyHiddenProducts() throws Exception {
        Member admin = saveMember("admin-search-admin3", Role.ADMIN);
        Member seller = saveMember("admin-search-seller3", Role.SELLER);
        Product visibleProduct = new Product(seller, "공개상품", "설명", 10000, 10, null, false, ProductCategory.ETC);
        Product hiddenProduct = new Product(seller, "숨김상품", "설명", 10000, 10, null, false, ProductCategory.ETC);
        hiddenProduct.hide();
        productRepository.save(visibleProduct);
        productRepository.save(hiddenProduct);

        mockMvc.perform(get("/api/admin/products")
                        .param("status", "HIDDEN")
                        .with(asUser(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("숨김상품"));
    }

    @Test
    @DisplayName("관리자가 status=VISIBLE 파라미터를 넘기면 오픈예정 상품은 빠지고 실제로 공개된 상품만 반환된다")
    void products_withVisibleStatusFilter_excludesUpcomingProducts() throws Exception {
        Member admin = saveMember("admin-search-admin6", Role.ADMIN);
        Member seller = saveMember("admin-search-seller6", Role.SELLER);

        Product openProduct = new Product(seller, "지금공개중상품", "설명", 10000, 10, null, false, ProductCategory.ETC, null);
        Product upcomingProduct = new Product(seller, "아직안열린상품", "설명", 10000, 10, null, false, ProductCategory.ETC,
                java.time.LocalDateTime.now().plusDays(3));
        productRepository.save(openProduct);
        productRepository.save(upcomingProduct);

        mockMvc.perform(get("/api/admin/products")
                        .param("status", "VISIBLE")
                        .with(asUser(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("지금공개중상품"));
    }

    @Test
    @DisplayName("관리자가 status=PUSH 파라미터를 넘기면 평점 4.5 이상인 추천 푸시 대상 상품만 필터링된다")
    void products_withPushStatusFilter_returnsOnlyPushCandidates() throws Exception {
        Member admin = saveMember("admin-search-admin4", Role.ADMIN);
        Member seller = saveMember("admin-search-seller4", Role.SELLER);
        Member reviewer = saveMember("admin-search-reviewer", Role.BUYER);

        Product pushProduct = new Product(seller, "인기추천상품", "설명", 10000, 10, null, false, ProductCategory.ETC);
        Product normalProduct = new Product(seller, "일반상품", "설명", 10000, 10, null, false, ProductCategory.ETC);
        productRepository.save(pushProduct);
        productRepository.save(normalProduct);

        Review highReview = new Review(pushProduct, reviewer, 5, "최고예요");
        Review lowReview = new Review(normalProduct, reviewer, 3, "보통이에요");
        reviewRepository.save(highReview);
        reviewRepository.save(lowReview);

        mockMvc.perform(get("/api/admin/products")
                        .param("status", "PUSH")
                        .with(asUser(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("인기추천상품"));
    }

    @Test
    @DisplayName("관리자가 status=UPCOMING 파라미터를 넘기면 오픈 예정 상품만 필터링되어 반환된다")
    void products_withUpcomingStatusFilter_returnsOnlyUpcomingProducts() throws Exception {
        Member admin = saveMember("admin-search-admin5", Role.ADMIN);
        Member seller = saveMember("admin-search-seller5", Role.SELLER);

        Product upcomingProduct = new Product(seller, "오픈예정상품", "설명", 10000, 10, null, false, ProductCategory.ETC, java.time.LocalDateTime.now().plusDays(3));
        Product normalProduct = new Product(seller, "일반공개상품", "설명", 10000, 10, null, false, ProductCategory.ETC, null);
        productRepository.save(upcomingProduct);
        productRepository.save(normalProduct);

        mockMvc.perform(get("/api/admin/products")
                        .param("status", "UPCOMING")
                        .with(asUser(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("오픈예정상품"));
    }

    @Test
    @DisplayName("관리자가 다른 회원을 정지하면 204, 그 회원의 suspended가 true로 바뀐다")
    void suspendMember_asAdmin_succeeds() throws Exception {
        Member admin = saveMember("admin-test-2", Role.ADMIN);
        Member target = saveMember("admin-test-target1", Role.BUYER);

        mockMvc.perform(post("/api/admin/members/" + target.getId() + "/suspend").with(asUser(admin)))
                .andExpect(status().isNoContent());

        Member reloaded = memberRepository.findById(target.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(reloaded.isSuspended());
    }

    @Test
    @DisplayName("정지 후 정지 해제하면 204, suspended가 다시 false로 바뀐다")
    void unsuspendMember_afterSuspend_succeeds() throws Exception {
        Member admin = saveMember("admin-test-3", Role.ADMIN);
        Member target = saveMember("admin-test-target2", Role.BUYER);
        target.suspend();
        memberRepository.save(target);

        mockMvc.perform(post("/api/admin/members/" + target.getId() + "/unsuspend").with(asUser(admin)))
                .andExpect(status().isNoContent());

        Member reloaded = memberRepository.findById(target.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertFalse(reloaded.isSuspended());
    }

    @Test
    @DisplayName("관리자가 자기 자신을 정지하려 하면 403 FORBIDDEN")
    void suspendMember_self_returnsForbidden() throws Exception {
        Member admin = saveMember("admin-test-4", Role.ADMIN);

        mockMvc.perform(post("/api/admin/members/" + admin.getId() + "/suspend").with(asUser(admin)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("관리자가 자기 자신을 삭제하려 하면 403 FORBIDDEN")
    void deleteMember_self_returnsForbidden() throws Exception {
        Member admin = saveMember("admin-test-5", Role.ADMIN);

        mockMvc.perform(delete("/api/admin/members/" + admin.getId()).with(asUser(admin)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("상품(판매자)을 등록한 회원은 삭제 시 409 MEMBER_HAS_ACTIVITY")
    void deleteMember_withProductActivity_returnsConflict() throws Exception {
        Member admin = saveMember("admin-test-6", Role.ADMIN);
        Member seller = saveMember("admin-test-seller1", Role.SELLER);
        saveProduct(seller);

        mockMvc.perform(delete("/api/admin/members/" + seller.getId()).with(asUser(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_HAS_ACTIVITY"));

        org.junit.jupiter.api.Assertions.assertTrue(memberRepository.findById(seller.getId()).isPresent());
    }

    @Test
    @DisplayName("리뷰를 작성한 회원은 삭제 시 409 MEMBER_HAS_ACTIVITY")
    void deleteMember_withReviewActivity_returnsConflict() throws Exception {
        Member admin = saveMember("admin-test-7", Role.ADMIN);
        Member seller = saveMember("admin-test-seller2", Role.SELLER);
        Member reviewer = saveMember("admin-test-reviewer1", Role.BUYER);
        Product product = saveProduct(seller);
        reviewRepository.save(new Review(product, reviewer, 5, "좋아요"));

        mockMvc.perform(delete("/api/admin/members/" + reviewer.getId()).with(asUser(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_HAS_ACTIVITY"));
    }

    @Test
    @DisplayName("결제 이력이 있는 회원은 삭제 시 409 MEMBER_HAS_ACTIVITY")
    void deleteMember_withPaymentActivity_returnsConflict() throws Exception {
        Member admin = saveMember("admin-test-8", Role.ADMIN);
        Member seller = saveMember("admin-test-seller3", Role.SELLER);
        Member buyer = saveMember("admin-test-buyer3", Role.BUYER);
        Product product = saveProduct(seller);
        paymentRepository.save(new Payment(buyer, product, null, 10000));

        mockMvc.perform(delete("/api/admin/members/" + buyer.getId()).with(asUser(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_HAS_ACTIVITY"));
    }

    @Test
    @DisplayName("챗봇 세션이 있는 회원은 삭제 시 409 MEMBER_HAS_ACTIVITY")
    void deleteMember_withChatSessionActivity_returnsConflict() throws Exception {
        Member admin = saveMember("admin-test-9", Role.ADMIN);
        Member buyer = saveMember("admin-test-buyer4", Role.BUYER);
        chatSessionRepository.save(new ChatSession(buyer));

        mockMvc.perform(delete("/api/admin/members/" + buyer.getId()).with(asUser(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_HAS_ACTIVITY"));
    }

    @Test
    @DisplayName("활동 기록이 전혀 없는 회원은 실제로 삭제된다")
    void deleteMember_withNoActivity_succeeds() throws Exception {
        Member admin = saveMember("admin-test-10", Role.ADMIN);
        Member target = saveMember("admin-test-target3", Role.BUYER);

        mockMvc.perform(delete("/api/admin/members/" + target.getId()).with(asUser(admin)))
                .andExpect(status().isNoContent());

        org.junit.jupiter.api.Assertions.assertTrue(memberRepository.findById(target.getId()).isEmpty());
    }

    @Test
    @DisplayName("존재하지 않는 회원 정지 시도 시 404 MEMBER_NOT_FOUND")
    void suspendMember_notFound_returns404() throws Exception {
        Member admin = saveMember("admin-test-11", Role.ADMIN);

        mockMvc.perform(post("/api/admin/members/999999/suspend").with(asUser(admin)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    @Test
    @DisplayName("관리자 대시보드는 요약 필드를 전부 포함한 200을 반환한다")
    void dashboard_asAdmin_returnsSummary() throws Exception {
        Member admin = saveMember("admin-test-12", Role.ADMIN);

        mockMvc.perform(get("/api/admin/dashboard").with(asUser(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalMembers").exists())
                .andExpect(jsonPath("$.data.totalBuyers").exists())
                .andExpect(jsonPath("$.data.totalSellers").exists())
                .andExpect(jsonPath("$.data.totalProducts").exists())
                .andExpect(jsonPath("$.data.totalPayments").exists())
                .andExpect(jsonPath("$.data.pendingRefundRequests").exists());
    }

    @Test
    @DisplayName("관리자 환불 요청 목록은 판매자 범위 없이 전체를 반환하고 status로 필터링된다")
    void refundRequests_asAdmin_filtersByStatus() throws Exception {
        Member admin = saveMember("admin-test-13", Role.ADMIN);
        Member seller = saveMember("admin-test-seller4", Role.SELLER);
        Member buyer = saveMember("admin-test-buyer5", Role.BUYER);
        Product product = saveProduct(seller);
        Payment payment = paymentRepository.save(new Payment(buyer, product, null, 10000));
        refundRequestRepository.save(new RefundRequest(payment, buyer, "단순 변심"));

        mockMvc.perform(get("/api/admin/refund-requests").param("status", "PENDING").with(asUser(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("관리자는 활동 기록이 없는 상품을 삭제할 수 있다")
    void deleteProduct_success() throws Exception {
        Member admin = saveMember("admin-test-p1", Role.ADMIN);
        Member seller = saveMember("admin-test-p-seller1", Role.SELLER);
        Product product = saveProduct(seller);

        mockMvc.perform(delete("/api/admin/products/" + product.getId()).with(asUser(admin)))
                .andExpect(status().isNoContent());

        org.junit.jupiter.api.Assertions.assertTrue(productRepository.findById(product.getId()).isEmpty());
    }

    @Test
    @DisplayName("결제가 있는 상품은 500이 아니라 409 PRODUCT_HAS_ACTIVITY로 거절한다")
    void deleteProduct_conflict_whenPaymentExists() throws Exception {
        // 회원 삭제(MEMBER_HAS_ACTIVITY)와 같은 정책이다.
        //
        // 가드가 없으면 payment.product_id의 FK(NO ACTION)가 DELETE를 거부해 500이 된다 —
        // 로컬 DB에서 직접 재현해 확인했다: ERROR 1451 (23000) Cannot delete or update a parent row.
        // 단, **이 테스트로는 그 경로를 보일 수 없다**. 테스트가 트랜잭션 롤백이라 DELETE가 DB까지
        // 가지 않아서, 가드를 지우면 500이 아니라 204가 나온다(실제로 확인함). 그래서 이 테스트가
        // 고정하는 건 "409로 거절한다"는 계약이고, FK가 막는다는 사실은 위 실측이 근거다.
        Member admin = saveMember("admin-test-p2", Role.ADMIN);
        Member seller = saveMember("admin-test-p-seller2", Role.SELLER);
        Member buyer = saveMember("admin-test-p-buyer2", Role.BUYER);
        Product product = saveProduct(seller);
        paymentRepository.save(new Payment(buyer, product, null, 10000));

        mockMvc.perform(delete("/api/admin/products/" + product.getId()).with(asUser(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_HAS_ACTIVITY"));

        org.junit.jupiter.api.Assertions.assertTrue(productRepository.findById(product.getId()).isPresent());
    }

    @Test
    @DisplayName("리뷰가 있는 상품도 409로 거절한다")
    void deleteProduct_conflict_whenReviewExists() throws Exception {
        Member admin = saveMember("admin-test-p3", Role.ADMIN);
        Member seller = saveMember("admin-test-p-seller3", Role.SELLER);
        Member reviewer = saveMember("admin-test-p-buyer3", Role.BUYER);
        Product product = saveProduct(seller);
        reviewRepository.save(new Review(product, reviewer, 5, "좋아요"));

        mockMvc.perform(delete("/api/admin/products/" + product.getId()).with(asUser(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_HAS_ACTIVITY"));
    }

    @Test
    @DisplayName("관리자가 아니면 상품 삭제는 403 FORBIDDEN")
    void deleteProduct_forbidden_whenNotAdmin() throws Exception {
        Member seller = saveMember("admin-test-p-seller4", Role.SELLER);
        Product product = saveProduct(seller);

        mockMvc.perform(delete("/api/admin/products/" + product.getId()).with(asUser(seller)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        org.junit.jupiter.api.Assertions.assertTrue(productRepository.findById(product.getId()).isPresent());
    }

    @Test
    @DisplayName("관리자가 상품을 숨기면 공개 목록·상세에서 사라지고, 해제하면 돌아온다")
    void hideProduct_removesFromPublicListAndDetail() throws Exception {
        Member admin = saveMember("admin-test-h1", Role.ADMIN);
        Member seller = saveMember("admin-test-h-seller1", Role.SELLER);
        Product product = saveProduct(seller);

        mockMvc.perform(patch("/api/admin/products/" + product.getId() + "/hidden")
                        .param("hidden", "true").with(asUser(admin)))
                .andExpect(status().isNoContent());

        // 숨김 상품은 **직접 링크로도** 열리지 않는다 — 목록에서만 빼면 주소를 아는 사람은 계속 본다.
        mockMvc.perform(get("/api/products/" + product.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));

        mockMvc.perform(patch("/api/admin/products/" + product.getId() + "/hidden")
                        .param("hidden", "false").with(asUser(admin)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/products/" + product.getId()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("관리자 상품 목록은 숨김 상품까지 보여준다 — 안 그러면 되돌릴 방법이 없다")
    void adminProductList_includesHidden() throws Exception {
        Member admin = saveMember("admin-test-h2", Role.ADMIN);
        Member seller = saveMember("admin-test-h-seller2", Role.SELLER);
        Product product = saveProduct(seller);
        mockMvc.perform(patch("/api/admin/products/" + product.getId() + "/hidden")
                .param("hidden", "true").with(asUser(admin)));

        mockMvc.perform(get("/api/admin/products").param("size", "100").with(asUser(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.productId == " + product.getId() + ")].hidden")
                        .value(org.hamcrest.Matchers.hasItem(true)));
    }

    @Test
    @DisplayName("관리자가 아니면 숨김 처리는 403 FORBIDDEN")
    void hideProduct_forbidden_whenNotAdmin() throws Exception {
        Member seller = saveMember("admin-test-h-seller3", Role.SELLER);
        Product product = saveProduct(seller);

        mockMvc.perform(patch("/api/admin/products/" + product.getId() + "/hidden")
                        .param("hidden", "true").with(asUser(seller)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("force=true면 결제가 있어도 상품과 결제를 함께 삭제한다")
    void forceDeleteProduct_removesPaymentsToo() throws Exception {
        Member admin = saveMember("admin-test-f1", Role.ADMIN);
        Member seller = saveMember("admin-test-f-seller1", Role.SELLER);
        Member buyer = saveMember("admin-test-f-buyer1", Role.BUYER);
        Product product = saveProduct(seller);
        Payment payment = paymentRepository.save(new Payment(buyer, product, null, 10000));

        // 강제 아니면 거절되는 상황인지 먼저 확인 — 그래야 force가 실제로 다른 일을 한다는 게 성립한다.
        mockMvc.perform(delete("/api/admin/products/" + product.getId()).with(asUser(admin)))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/admin/products/" + product.getId())
                        .param("force", "true").with(asUser(admin)))
                .andExpect(status().isNoContent());

        org.junit.jupiter.api.Assertions.assertTrue(productRepository.findById(product.getId()).isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(paymentRepository.findById(payment.getId()).isEmpty(),
                "강제 삭제는 그 상품의 결제까지 지운다");
    }
}
