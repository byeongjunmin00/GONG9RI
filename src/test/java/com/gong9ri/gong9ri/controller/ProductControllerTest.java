package com.gong9ri.gong9ri.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.PriceTier;
import com.gong9ri.gong9ri.entity.Product;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.repository.MemberRepository;
import com.gong9ri.gong9ri.repository.PriceTierRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import java.util.List;
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

    private Map<String, Object> registerRequestBody() {
        return Map.of(
                "name", "제주 감귤 5kg",
                "description", "직접 재배한 감귤",
                "basePrice", 25000,
                "maxParticipants", 10,
                "priceTiers", List.of(
                        Map.of("minCount", 2, "price", 22000),
                        Map.of("minCount", 10, "price", 15000)
                )
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
                .andExpect(jsonPath("$.data.sellerId").value(seller.getId().intValue()));
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
    @DisplayName("본인 상품 수정 시 200")
    void update_success_owner() throws Exception {
        Member seller = saveMember("seller5", Role.SELLER);
        Product product = saveProduct(seller);

        Map<String, Object> updateBody = Map.of(
                "name", "수정된 이름",
                "description", "수정된 설명",
                "basePrice", 30000,
                "maxParticipants", 8,
                "priceTiers", List.of(Map.of("minCount", 2, "price", 20000))
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
}
