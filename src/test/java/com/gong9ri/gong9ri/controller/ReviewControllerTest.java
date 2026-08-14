package com.gong9ri.gong9ri.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Payment;
import com.gong9ri.gong9ri.entity.Product;
import com.gong9ri.gong9ri.entity.Review;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.repository.MemberRepository;
import com.gong9ri.gong9ri.repository.PaymentRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import com.gong9ri.gong9ri.repository.ReviewRepository;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * 상품 리뷰 — 실제로 결제 완료(PAID)한 구매자만 작성할 수 있고, 상품·회원 조합당 하나만 허용된다
 * (docs/dev/review/design.md).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    private Member saveMember(String username, Role role) {
        Member member = new Member(username, "encoded-password", "테스트유저", username + "@test.com", role);
        return memberRepository.save(member);
    }

    private Product saveProduct(Member seller) {
        return productRepository.save(new Product(seller, "제주 감귤 5kg", "설명", 25000, 10, null));
    }

    private RequestPostProcessor asUser(Member member) {
        MemberUserDetails principal = new MemberUserDetails(member);
        UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        return authentication(token);
    }

    private String toJson(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    @Test
    @DisplayName("결제 완료한 구매자가 리뷰를 작성하면 201")
    void create_success() throws Exception {
        Member seller = saveMember("reviewSeller1", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("reviewBuyer1", Role.BUYER);
        paymentRepository.save(new Payment(buyer, product, null, 25000));

        mockMvc.perform(post("/api/products/" + product.getId() + "/reviews")
                        .with(asUser(buyer))
                        .contentType("application/json")
                        .content(toJson(Map.of("rating", 5, "content", "정말 만족스러웠어요"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.rating").value(5))
                .andExpect(jsonPath("$.data.content").value("정말 만족스러웠어요"))
                .andExpect(jsonPath("$.data.memberName").value("테스트유저"));
    }

    @Test
    @DisplayName("결제 이력이 없는 회원이 리뷰를 작성하면 403 REVIEW_NOT_ELIGIBLE")
    void create_notEligible() throws Exception {
        Member seller = saveMember("reviewSeller2", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("reviewBuyer2", Role.BUYER);

        mockMvc.perform(post("/api/products/" + product.getId() + "/reviews")
                        .with(asUser(buyer))
                        .contentType("application/json")
                        .content(toJson(Map.of("rating", 5, "content", "구매 안 했는데 씀"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_ELIGIBLE"));
    }

    @Test
    @DisplayName("같은 상품에 이미 리뷰를 쓴 회원이 또 쓰려 하면 409 DUPLICATE_REVIEW")
    void create_duplicate() throws Exception {
        Member seller = saveMember("reviewSeller3", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("reviewBuyer3", Role.BUYER);
        paymentRepository.save(new Payment(buyer, product, null, 25000));
        reviewRepository.save(new Review(product, buyer, 4, "먼저 쓴 리뷰"));

        mockMvc.perform(post("/api/products/" + product.getId() + "/reviews")
                        .with(asUser(buyer))
                        .contentType("application/json")
                        .content(toJson(Map.of("rating", 5, "content", "또 씀"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_REVIEW"));
    }

    @Test
    @DisplayName("평점이 1~5 범위를 벗어나면 400 VALIDATION_FAILED")
    void create_invalidRating() throws Exception {
        Member seller = saveMember("reviewSeller4", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("reviewBuyer4", Role.BUYER);
        paymentRepository.save(new Payment(buyer, product, null, 25000));

        mockMvc.perform(post("/api/products/" + product.getId() + "/reviews")
                        .with(asUser(buyer))
                        .contentType("application/json")
                        .content(toJson(Map.of("rating", 6, "content", "범위 밖"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("비로그인으로 리뷰를 작성하면 401 UNAUTHORIZED")
    void create_unauthorized() throws Exception {
        Member seller = saveMember("reviewSeller5", Role.SELLER);
        Product product = saveProduct(seller);

        mockMvc.perform(post("/api/products/" + product.getId() + "/reviews")
                        .contentType("application/json")
                        .content(toJson(Map.of("rating", 5, "content", "비로그인"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("리뷰 목록 조회는 비로그인도 가능하고, 평균 평점·개수를 함께 반환한다")
    void list_success_publicWithAverage() throws Exception {
        Member seller = saveMember("reviewSeller6", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer1 = saveMember("reviewBuyer6a", Role.BUYER);
        Member buyer2 = saveMember("reviewBuyer6b", Role.BUYER);
        reviewRepository.save(new Review(product, buyer1, 4, "괜찮아요"));
        reviewRepository.save(new Review(product, buyer2, 2, "별로예요"));

        mockMvc.perform(get("/api/products/" + product.getId() + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(2))
                .andExpect(jsonPath("$.data.averageRating").value(3.0))
                .andExpect(jsonPath("$.data.reviews.length()").value(2));
    }

    @Test
    @DisplayName("리뷰가 하나도 없으면 평균 평점은 null, 개수는 0이다")
    void list_empty() throws Exception {
        Member seller = saveMember("reviewSeller7", Role.SELLER);
        Product product = saveProduct(seller);

        mockMvc.perform(get("/api/products/" + product.getId() + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(0))
                .andExpect(jsonPath("$.data.averageRating").doesNotExist())
                .andExpect(jsonPath("$.data.reviews.length()").value(0));
    }

    @Test
    @DisplayName("본인이 쓴 리뷰를 수정하면 200")
    void update_success() throws Exception {
        Member seller = saveMember("reviewSeller8", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("reviewBuyer8", Role.BUYER);
        Review review = reviewRepository.save(new Review(product, buyer, 3, "그냥 그래요"));

        mockMvc.perform(put("/api/reviews/" + review.getId())
                        .with(asUser(buyer))
                        .contentType("application/json")
                        .content(toJson(Map.of("rating", 5, "content", "다시 써보니 좋아요"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rating").value(5))
                .andExpect(jsonPath("$.data.content").value("다시 써보니 좋아요"));
    }

    @Test
    @DisplayName("타인의 리뷰를 수정하려 하면 403 FORBIDDEN")
    void update_forbidden_notOwner() throws Exception {
        Member seller = saveMember("reviewSeller9", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("reviewBuyer9", Role.BUYER);
        Member other = saveMember("reviewBuyer9b", Role.BUYER);
        Review review = reviewRepository.save(new Review(product, buyer, 3, "원 리뷰"));

        mockMvc.perform(put("/api/reviews/" + review.getId())
                        .with(asUser(other))
                        .contentType("application/json")
                        .content(toJson(Map.of("rating", 1, "content", "남의 리뷰 수정 시도"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("본인이 쓴 리뷰를 삭제하면 204이고 실제로 삭제된다")
    void delete_success() throws Exception {
        Member seller = saveMember("reviewSeller10", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("reviewBuyer10", Role.BUYER);
        Review review = reviewRepository.save(new Review(product, buyer, 3, "삭제될 리뷰"));

        mockMvc.perform(delete("/api/reviews/" + review.getId()).with(asUser(buyer)))
                .andExpect(status().isNoContent());

        assertTrue(reviewRepository.findById(review.getId()).isEmpty());
    }

    @Test
    @DisplayName("타인의 리뷰를 삭제하려 하면 403 FORBIDDEN이고 삭제되지 않는다")
    void delete_forbidden_notOwner() throws Exception {
        Member seller = saveMember("reviewSeller11", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("reviewBuyer11", Role.BUYER);
        Member other = saveMember("reviewBuyer11b", Role.BUYER);
        Review review = reviewRepository.save(new Review(product, buyer, 3, "지워지면 안 되는 리뷰"));

        mockMvc.perform(delete("/api/reviews/" + review.getId()).with(asUser(other)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertEquals(1, reviewRepository.findById(review.getId()).stream().count());
    }

    @Test
    @DisplayName("존재하지 않는 상품에 리뷰를 작성하면 404 PRODUCT_NOT_FOUND")
    void create_productNotFound() throws Exception {
        Member buyer = saveMember("reviewBuyer12", Role.BUYER);

        mockMvc.perform(post("/api/products/999999/reviews")
                        .with(asUser(buyer))
                        .contentType("application/json")
                        .content(toJson(Map.of("rating", 5, "content", "존재 안 하는 상품"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }
}
