package com.gong9ri.gong9ri.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.entity.Inquiry;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Product;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.repository.InquiryRepository;
import com.gong9ri.gong9ri.repository.MemberRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
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
 * 상품 문의 — 리뷰와 달리 구매 이력 없이 누구나(로그인만 하면) 작성할 수 있고, 답변이 등록되면
 * 작성자가 더 이상 손댈 수 없다. 그 상품을 등록한 판매자 본인만 답변을 등록·수정·삭제할 수 있다
 * (docs/dev/ongoing/product-inquiry.md, docs/api/inquiry.md).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InquiryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InquiryRepository inquiryRepository;

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
    @DisplayName("비로그인도 문의 목록을 조회할 수 있다")
    void list_success_public() throws Exception {
        Member seller = saveMember("inqSeller1", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("inqBuyer1", Role.BUYER);
        inquiryRepository.save(new Inquiry(product, buyer, "배송은 얼마나 걸리나요?"));

        mockMvc.perform(get("/api/products/" + product.getId() + "/inquiries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(1))
                .andExpect(jsonPath("$.data.inquiries[0].answered").value(false));
    }

    @Test
    @DisplayName("존재하지 않는 상품의 문의 목록을 조회하면 404 PRODUCT_NOT_FOUND")
    void list_productNotFound() throws Exception {
        mockMvc.perform(get("/api/products/999999/inquiries"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    @DisplayName("비로그인으로 문의를 작성하면 401 UNAUTHORIZED")
    void create_unauthorized() throws Exception {
        Member seller = saveMember("inqSeller2", Role.SELLER);
        Product product = saveProduct(seller);

        mockMvc.perform(post("/api/products/" + product.getId() + "/inquiries")
                        .contentType("application/json")
                        .content(toJson(Map.of("content", "비로그인 문의"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("구매 이력이 없는 로그인 구매자도 문의를 작성하면 201 (리뷰와 달리 구매 이력 불필요)")
    void create_success_withoutPurchaseHistory() throws Exception {
        Member seller = saveMember("inqSeller3", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("inqBuyer3", Role.BUYER);

        mockMvc.perform(post("/api/products/" + product.getId() + "/inquiries")
                        .with(asUser(buyer))
                        .contentType("application/json")
                        .content(toJson(Map.of("content", "옵션 색상이 궁금해요"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.content").value("옵션 색상이 궁금해요"))
                .andExpect(jsonPath("$.data.answered").value(false))
                .andExpect(jsonPath("$.data.memberName").value("테스트유저"));
    }

    @Test
    @DisplayName("존재하지 않는 상품에 문의를 작성하면 404 PRODUCT_NOT_FOUND")
    void create_productNotFound() throws Exception {
        Member buyer = saveMember("inqBuyer4", Role.BUYER);

        mockMvc.perform(post("/api/products/999999/inquiries")
                        .with(asUser(buyer))
                        .contentType("application/json")
                        .content(toJson(Map.of("content", "존재 안 하는 상품"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    @DisplayName("content가 공백이면 400 VALIDATION_FAILED")
    void create_blankContent() throws Exception {
        Member seller = saveMember("inqSeller5", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("inqBuyer5", Role.BUYER);

        mockMvc.perform(post("/api/products/" + product.getId() + "/inquiries")
                        .with(asUser(buyer))
                        .contentType("application/json")
                        .content(toJson(Map.of("content", "   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("본인이 쓴 문의를 수정하면 200")
    void update_success() throws Exception {
        Member seller = saveMember("inqSeller6", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("inqBuyer6", Role.BUYER);
        Inquiry inquiry = inquiryRepository.save(new Inquiry(product, buyer, "원래 문의"));

        mockMvc.perform(put("/api/inquiries/" + inquiry.getId())
                        .with(asUser(buyer))
                        .contentType("application/json")
                        .content(toJson(Map.of("content", "수정된 문의"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("수정된 문의"));
    }

    @Test
    @DisplayName("타인이 쓴 문의를 수정하려 하면 403 FORBIDDEN")
    void update_forbidden_notOwner() throws Exception {
        Member seller = saveMember("inqSeller7", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("inqBuyer7", Role.BUYER);
        Member other = saveMember("inqBuyer7b", Role.BUYER);
        Inquiry inquiry = inquiryRepository.save(new Inquiry(product, buyer, "원래 문의"));

        mockMvc.perform(put("/api/inquiries/" + inquiry.getId())
                        .with(asUser(other))
                        .contentType("application/json")
                        .content(toJson(Map.of("content", "남의 문의 수정 시도"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("답변이 등록된 문의를 작성자가 수정하려 하면 409 INQUIRY_ALREADY_ANSWERED")
    void update_alreadyAnswered() throws Exception {
        Member seller = saveMember("inqSeller8", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("inqBuyer8", Role.BUYER);
        Inquiry inquiry = inquiryRepository.save(new Inquiry(product, buyer, "원래 문의"));
        inquiry.registerAnswer(seller, "답변입니다");

        mockMvc.perform(put("/api/inquiries/" + inquiry.getId())
                        .with(asUser(buyer))
                        .contentType("application/json")
                        .content(toJson(Map.of("content", "답변 달렸는데 수정 시도"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INQUIRY_ALREADY_ANSWERED"));
    }

    @Test
    @DisplayName("존재하지 않는 문의를 수정하려 하면 404 INQUIRY_NOT_FOUND")
    void update_inquiryNotFound() throws Exception {
        Member buyer = saveMember("inqBuyer9", Role.BUYER);

        mockMvc.perform(put("/api/inquiries/999999")
                        .with(asUser(buyer))
                        .contentType("application/json")
                        .content(toJson(Map.of("content", "없는 문의 수정 시도"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INQUIRY_NOT_FOUND"));
    }

    @Test
    @DisplayName("본인이 쓴 문의를 삭제하면 204이고 실제로 삭제된다")
    void delete_success() throws Exception {
        Member seller = saveMember("inqSeller10", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("inqBuyer10", Role.BUYER);
        Inquiry inquiry = inquiryRepository.save(new Inquiry(product, buyer, "삭제될 문의"));

        mockMvc.perform(delete("/api/inquiries/" + inquiry.getId()).with(asUser(buyer)))
                .andExpect(status().isNoContent());

        assertTrue(inquiryRepository.findById(inquiry.getId()).isEmpty());
    }

    @Test
    @DisplayName("타인이 쓴 문의를 삭제하려 하면 403 FORBIDDEN이고 삭제되지 않는다")
    void delete_forbidden_notOwner() throws Exception {
        Member seller = saveMember("inqSeller11", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("inqBuyer11", Role.BUYER);
        Member other = saveMember("inqBuyer11b", Role.BUYER);
        Inquiry inquiry = inquiryRepository.save(new Inquiry(product, buyer, "지워지면 안 되는 문의"));

        mockMvc.perform(delete("/api/inquiries/" + inquiry.getId()).with(asUser(other)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertTrue(inquiryRepository.findById(inquiry.getId()).isPresent());
    }

    @Test
    @DisplayName("답변이 등록된 문의를 작성자가 삭제하려 하면 409 INQUIRY_ALREADY_ANSWERED이고 삭제되지 않는다")
    void delete_alreadyAnswered() throws Exception {
        Member seller = saveMember("inqSeller12", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("inqBuyer12", Role.BUYER);
        Inquiry inquiry = inquiryRepository.save(new Inquiry(product, buyer, "원래 문의"));
        inquiry.registerAnswer(seller, "답변입니다");

        mockMvc.perform(delete("/api/inquiries/" + inquiry.getId()).with(asUser(buyer)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INQUIRY_ALREADY_ANSWERED"));

        assertTrue(inquiryRepository.findById(inquiry.getId()).isPresent());
    }

    @Test
    @DisplayName("그 상품의 판매자가 답변을 등록하면 201이고 answered가 true로 바뀐다")
    void registerAnswer_success() throws Exception {
        Member seller = saveMember("inqSeller13", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("inqBuyer13", Role.BUYER);
        Inquiry inquiry = inquiryRepository.save(new Inquiry(product, buyer, "배송 문의"));

        mockMvc.perform(post("/api/inquiries/" + inquiry.getId() + "/answer")
                        .with(asUser(seller))
                        .contentType("application/json")
                        .content(toJson(Map.of("content", "평균 2~3일 소요됩니다."))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.answered").value(true))
                .andExpect(jsonPath("$.data.answerContent").value("평균 2~3일 소요됩니다."));
    }

    @Test
    @DisplayName("구매자 계정이 답변을 등록하려 하면 403 FORBIDDEN")
    void registerAnswer_forbidden_buyer() throws Exception {
        Member seller = saveMember("inqSeller14", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("inqBuyer14", Role.BUYER);
        Inquiry inquiry = inquiryRepository.save(new Inquiry(product, buyer, "배송 문의"));

        mockMvc.perform(post("/api/inquiries/" + inquiry.getId() + "/answer")
                        .with(asUser(buyer))
                        .contentType("application/json")
                        .content(toJson(Map.of("content", "구매자가 답변 시도"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("다른 상품의 판매자가 답변을 등록하려 하면 403 FORBIDDEN")
    void registerAnswer_forbidden_otherSeller() throws Exception {
        Member seller = saveMember("inqSeller15", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("inqBuyer15", Role.BUYER);
        Inquiry inquiry = inquiryRepository.save(new Inquiry(product, buyer, "배송 문의"));
        Member otherSeller = saveMember("inqSeller15b", Role.SELLER);

        mockMvc.perform(post("/api/inquiries/" + inquiry.getId() + "/answer")
                        .with(asUser(otherSeller))
                        .contentType("application/json")
                        .content(toJson(Map.of("content", "다른 판매자가 답변 시도"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("이미 답변된 문의에 재등록을 시도하면 409 INQUIRY_ALREADY_ANSWERED")
    void registerAnswer_alreadyAnswered() throws Exception {
        Member seller = saveMember("inqSeller16", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("inqBuyer16", Role.BUYER);
        Inquiry inquiry = inquiryRepository.save(new Inquiry(product, buyer, "배송 문의"));
        inquiry.registerAnswer(seller, "이미 답변함");

        mockMvc.perform(post("/api/inquiries/" + inquiry.getId() + "/answer")
                        .with(asUser(seller))
                        .contentType("application/json")
                        .content(toJson(Map.of("content", "재등록 시도"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INQUIRY_ALREADY_ANSWERED"));
    }

    @Test
    @DisplayName("존재하지 않는 문의에 답변을 등록하려 하면 404 INQUIRY_NOT_FOUND")
    void registerAnswer_inquiryNotFound() throws Exception {
        Member seller = saveMember("inqSeller17", Role.SELLER);

        mockMvc.perform(post("/api/inquiries/999999/answer")
                        .with(asUser(seller))
                        .contentType("application/json")
                        .content(toJson(Map.of("content", "없는 문의 답변 시도"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INQUIRY_NOT_FOUND"));
    }

    @Test
    @DisplayName("답변이 없는 문의를 수정하려 하면 404 ANSWER_NOT_FOUND")
    void updateAnswer_answerNotFound() throws Exception {
        Member seller = saveMember("inqSeller18", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("inqBuyer18", Role.BUYER);
        Inquiry inquiry = inquiryRepository.save(new Inquiry(product, buyer, "배송 문의"));

        mockMvc.perform(put("/api/inquiries/" + inquiry.getId() + "/answer")
                        .with(asUser(seller))
                        .contentType("application/json")
                        .content(toJson(Map.of("content", "답변 없는데 수정 시도"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ANSWER_NOT_FOUND"));
    }

    @Test
    @DisplayName("답변이 없는 문의의 답변을 삭제하려 하면 404 ANSWER_NOT_FOUND")
    void deleteAnswer_answerNotFound() throws Exception {
        Member seller = saveMember("inqSeller19", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("inqBuyer19", Role.BUYER);
        Inquiry inquiry = inquiryRepository.save(new Inquiry(product, buyer, "배송 문의"));

        mockMvc.perform(delete("/api/inquiries/" + inquiry.getId() + "/answer").with(asUser(seller)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ANSWER_NOT_FOUND"));
    }

    @Test
    @DisplayName("판매자 본인이 자신이 등록한 답변을 수정하면 200")
    void updateAnswer_success() throws Exception {
        Member seller = saveMember("inqSeller20", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("inqBuyer20", Role.BUYER);
        Inquiry inquiry = inquiryRepository.save(new Inquiry(product, buyer, "배송 문의"));
        inquiry.registerAnswer(seller, "기존 답변");

        mockMvc.perform(put("/api/inquiries/" + inquiry.getId() + "/answer")
                        .with(asUser(seller))
                        .contentType("application/json")
                        .content(toJson(Map.of("content", "수정된 답변"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answerContent").value("수정된 답변"))
                .andExpect(jsonPath("$.data.answered").value(true));
    }

    @Test
    @DisplayName("다른 판매자가 답변을 수정하려 하면 403 FORBIDDEN")
    void updateAnswer_forbidden_otherSeller() throws Exception {
        Member seller = saveMember("inqSeller21", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("inqBuyer21", Role.BUYER);
        Inquiry inquiry = inquiryRepository.save(new Inquiry(product, buyer, "배송 문의"));
        inquiry.registerAnswer(seller, "기존 답변");
        Member otherSeller = saveMember("inqSeller21b", Role.SELLER);

        mockMvc.perform(put("/api/inquiries/" + inquiry.getId() + "/answer")
                        .with(asUser(otherSeller))
                        .contentType("application/json")
                        .content(toJson(Map.of("content", "다른 판매자가 수정 시도"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("판매자 본인이 자신이 등록한 답변을 삭제하면 204이고 문의는 남아 미답변 상태가 된다")
    void deleteAnswer_success() throws Exception {
        Member seller = saveMember("inqSeller22", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("inqBuyer22", Role.BUYER);
        Inquiry inquiry = inquiryRepository.save(new Inquiry(product, buyer, "배송 문의"));
        inquiry.registerAnswer(seller, "기존 답변");

        mockMvc.perform(delete("/api/inquiries/" + inquiry.getId() + "/answer").with(asUser(seller)))
                .andExpect(status().isNoContent());

        Inquiry reloaded = inquiryRepository.findById(inquiry.getId()).orElseThrow();
        assertNull(reloaded.getAnswerContent());
        assertNull(reloaded.getAnsweredAt());
        assertEquals("배송 문의", reloaded.getContent());
    }

    @Test
    @DisplayName("다른 판매자가 답변을 삭제하려 하면 403 FORBIDDEN이고 답변은 그대로 남는다")
    void deleteAnswer_forbidden_otherSeller() throws Exception {
        Member seller = saveMember("inqSeller23", Role.SELLER);
        Product product = saveProduct(seller);
        Member buyer = saveMember("inqBuyer23", Role.BUYER);
        Inquiry inquiry = inquiryRepository.save(new Inquiry(product, buyer, "배송 문의"));
        inquiry.registerAnswer(seller, "기존 답변");
        Member otherSeller = saveMember("inqSeller23b", Role.SELLER);

        mockMvc.perform(delete("/api/inquiries/" + inquiry.getId() + "/answer").with(asUser(otherSeller)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        Inquiry reloaded = inquiryRepository.findById(inquiry.getId()).orElseThrow();
        assertEquals("기존 답변", reloaded.getAnswerContent());
    }

    @Test
    @DisplayName("존재하지 않는 문의의 답변을 삭제하려 하면 404 INQUIRY_NOT_FOUND")
    void deleteAnswer_inquiryNotFound() throws Exception {
        Member seller = saveMember("inqSeller24", Role.SELLER);

        mockMvc.perform(delete("/api/inquiries/999999/answer").with(asUser(seller)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INQUIRY_NOT_FOUND"));
    }
}
