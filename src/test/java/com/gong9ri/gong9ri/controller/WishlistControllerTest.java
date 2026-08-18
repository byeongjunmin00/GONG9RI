package com.gong9ri.gong9ri.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Product;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.repository.MemberRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * 찜(product/wishlist) — 구매자 전용, 추가/제거 둘 다 멱등(docs/dev/product/wishlist/design.md).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WishlistControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductRepository productRepository;

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

    @Test
    @DisplayName("구매자가 상품을 찜하면 201, 마이페이지 목록에 반영된다")
    void add_success_reflectedInMypageList() throws Exception {
        Member seller = saveMember("wishlistSeller1", Role.SELLER);
        Member buyer = saveMember("wishlistBuyer1", Role.BUYER);
        Product product = saveProduct(seller);

        mockMvc.perform(post("/api/products/" + product.getId() + "/wishlist").with(asUser(buyer)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/buyer/mypage/wishlist").with(asUser(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].productId").value(product.getId().intValue()));
    }

    @Test
    @DisplayName("같은 상품을 두 번 찜해도(멱등) 에러 없이 성공하고 목록엔 하나만 남는다")
    void add_twice_isIdempotent() throws Exception {
        Member seller = saveMember("wishlistSeller2", Role.SELLER);
        Member buyer = saveMember("wishlistBuyer2", Role.BUYER);
        Product product = saveProduct(seller);

        mockMvc.perform(post("/api/products/" + product.getId() + "/wishlist").with(asUser(buyer)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/products/" + product.getId() + "/wishlist").with(asUser(buyer)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/buyer/mypage/wishlist").with(asUser(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    @DisplayName("찜 해제(DELETE)는 204이고, 찜 안 한 상품을 해제해도(멱등) 에러 없이 204다")
    void remove_success_andIdempotentWhenNotWishlisted() throws Exception {
        Member seller = saveMember("wishlistSeller3", Role.SELLER);
        Member buyer = saveMember("wishlistBuyer3", Role.BUYER);
        Product product = saveProduct(seller);

        mockMvc.perform(post("/api/products/" + product.getId() + "/wishlist").with(asUser(buyer)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/products/" + product.getId() + "/wishlist").with(asUser(buyer)))
                .andExpect(status().isNoContent());

        // 이미 해제된 상태에서 다시 해제해도(멱등) 에러 없이 204.
        mockMvc.perform(delete("/api/products/" + product.getId() + "/wishlist").with(asUser(buyer)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/buyer/mypage/wishlist").with(asUser(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("판매자 계정으로 찜을 시도하면 403 FORBIDDEN")
    void add_forbidden_sellerAccount() throws Exception {
        Member seller = saveMember("wishlistSeller4", Role.SELLER);
        Product product = saveProduct(seller);

        mockMvc.perform(post("/api/products/" + product.getId() + "/wishlist").with(asUser(seller)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("비로그인으로 찜을 시도하면 401 UNAUTHORIZED")
    void add_unauthorized_noLogin() throws Exception {
        Member seller = saveMember("wishlistSeller5", Role.SELLER);
        Product product = saveProduct(seller);

        mockMvc.perform(post("/api/products/" + product.getId() + "/wishlist"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("존재하지 않는 상품을 찜하려 하면 404 PRODUCT_NOT_FOUND")
    void add_notFound_nonExistentProduct() throws Exception {
        Member buyer = saveMember("wishlistBuyer6", Role.BUYER);

        mockMvc.perform(post("/api/products/999999/wishlist").with(asUser(buyer)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }
}
