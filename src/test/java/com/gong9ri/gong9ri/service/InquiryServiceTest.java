package com.gong9ri.gong9ri.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gong9ri.gong9ri.common.exception.BusinessException;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.dto.InquiryAnswerRequest;
import com.gong9ri.gong9ri.dto.InquiryCreateRequest;
import com.gong9ri.gong9ri.entity.Inquiry;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Product;
import com.gong9ri.gong9ri.repository.InquiryRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@code InquiryService} 순수 단위 테스트(Mockito) — 소유권/판매자/답변 상태 검증 로직만 초점으로
 * 본다(엔드투엔드 시나리오는 {@code InquiryControllerTest}, docs/dev/ongoing/product-inquiry.md).
 */
@ExtendWith(MockitoExtension.class)
class InquiryServiceTest {

    @Mock
    private InquiryRepository inquiryRepository;

    @Mock
    private ProductRepository productRepository;

    private InquiryService inquiryService;

    private Product product;
    private Member buyer;
    private Member seller;
    private Member otherMember;

    @BeforeEach
    void setUp() {
        inquiryService = new InquiryService(inquiryRepository, productRepository);

        product = mock(Product.class);
        buyer = mock(Member.class);
        seller = mock(Member.class);
        otherMember = mock(Member.class);
    }

    private MemberUserDetails principalOf(Member member) {
        return new MemberUserDetails(member);
    }

    @Test
    @DisplayName("create: 존재하지 않는 상품이면 PRODUCT_NOT_FOUND")
    void create_productNotFound() {
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> inquiryService.create(principalOf(buyer), 1L, new InquiryCreateRequest("문의")));

        assertEquals("PRODUCT_NOT_FOUND", exception.getErrorCode().name());
    }

    @Test
    @DisplayName("create: 상품이 존재하면 문의를 저장한다")
    void create_success() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(inquiryRepository.save(org.mockito.ArgumentMatchers.any(Inquiry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(buyer.getName()).thenReturn("구매자");

        var response = inquiryService.create(principalOf(buyer), 1L, new InquiryCreateRequest("문의합니다"));

        assertEquals("문의합니다", response.content());
        assertFalse(response.answered());
        verify(inquiryRepository).save(org.mockito.ArgumentMatchers.any(Inquiry.class));
    }

    @Test
    @DisplayName("list: 존재하지 않는 상품이면 PRODUCT_NOT_FOUND")
    void list_productNotFound() {
        when(productRepository.existsById(1L)).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class, () -> inquiryService.list(1L));

        assertEquals("PRODUCT_NOT_FOUND", exception.getErrorCode().name());
    }

    @Test
    @DisplayName("update: 존재하지 않는 문의면 INQUIRY_NOT_FOUND")
    void update_inquiryNotFound() {
        when(inquiryRepository.findById(1L)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> inquiryService.update(principalOf(buyer), 1L, new InquiryCreateRequest("수정")));

        assertEquals("INQUIRY_NOT_FOUND", exception.getErrorCode().name());
    }

    @Test
    @DisplayName("update: 작성자 본인이 아니면 FORBIDDEN")
    void update_notOwner_forbidden() {
        Inquiry inquiry = new Inquiry(product, buyer, "원래 문의");
        when(inquiryRepository.findById(1L)).thenReturn(Optional.of(inquiry));
        when(buyer.getId()).thenReturn(1L);
        when(otherMember.getId()).thenReturn(2L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> inquiryService.update(principalOf(otherMember), 1L, new InquiryCreateRequest("수정 시도")));

        assertEquals("FORBIDDEN", exception.getErrorCode().name());
    }

    @Test
    @DisplayName("update: 이미 답변된 문의면 작성자 본인이어도 INQUIRY_ALREADY_ANSWERED")
    void update_alreadyAnswered() {
        Inquiry inquiry = new Inquiry(product, buyer, "원래 문의");
        inquiry.registerAnswer(seller, "답변");
        when(inquiryRepository.findById(1L)).thenReturn(Optional.of(inquiry));
        when(buyer.getId()).thenReturn(1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> inquiryService.update(principalOf(buyer), 1L, new InquiryCreateRequest("수정 시도")));

        assertEquals("INQUIRY_ALREADY_ANSWERED", exception.getErrorCode().name());
    }

    @Test
    @DisplayName("update: 작성자 본인이고 미답변이면 내용이 수정된다")
    void update_success() {
        Inquiry inquiry = new Inquiry(product, buyer, "원래 문의");
        when(inquiryRepository.findById(1L)).thenReturn(Optional.of(inquiry));
        when(buyer.getId()).thenReturn(1L);

        var response = inquiryService.update(principalOf(buyer), 1L, new InquiryCreateRequest("수정된 문의"));

        assertEquals("수정된 문의", response.content());
    }

    @Test
    @DisplayName("delete: 답변이 등록된 문의면 INQUIRY_ALREADY_ANSWERED이고 삭제되지 않는다")
    void delete_alreadyAnswered() {
        Inquiry inquiry = new Inquiry(product, buyer, "원래 문의");
        inquiry.registerAnswer(seller, "답변");
        when(inquiryRepository.findById(1L)).thenReturn(Optional.of(inquiry));
        when(buyer.getId()).thenReturn(1L);

        assertThrows(BusinessException.class, () -> inquiryService.delete(principalOf(buyer), 1L));

        verify(inquiryRepository, org.mockito.Mockito.never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("registerAnswer: 그 상품의 판매자가 아니면 FORBIDDEN")
    void registerAnswer_notSeller_forbidden() {
        Inquiry inquiry = new Inquiry(product, buyer, "원래 문의");
        when(inquiryRepository.findById(1L)).thenReturn(Optional.of(inquiry));
        when(product.getSeller()).thenReturn(seller);
        when(seller.getId()).thenReturn(10L);
        when(otherMember.getId()).thenReturn(20L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> inquiryService.registerAnswer(principalOf(otherMember), 1L, new InquiryAnswerRequest("답변 시도")));

        assertEquals("FORBIDDEN", exception.getErrorCode().name());
    }

    @Test
    @DisplayName("registerAnswer: 이미 답변된 문의면 판매자 본인이어도 INQUIRY_ALREADY_ANSWERED")
    void registerAnswer_alreadyAnswered() {
        Inquiry inquiry = new Inquiry(product, buyer, "원래 문의");
        inquiry.registerAnswer(seller, "기존 답변");
        when(inquiryRepository.findById(1L)).thenReturn(Optional.of(inquiry));
        when(product.getSeller()).thenReturn(seller);
        when(seller.getId()).thenReturn(10L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> inquiryService.registerAnswer(principalOf(seller), 1L, new InquiryAnswerRequest("재등록 시도")));

        assertEquals("INQUIRY_ALREADY_ANSWERED", exception.getErrorCode().name());
    }

    @Test
    @DisplayName("registerAnswer: 판매자 본인이고 미답변이면 답변이 등록된다")
    void registerAnswer_success() {
        Inquiry inquiry = new Inquiry(product, buyer, "원래 문의");
        when(inquiryRepository.findById(1L)).thenReturn(Optional.of(inquiry));
        when(product.getSeller()).thenReturn(seller);
        when(seller.getId()).thenReturn(10L);

        var response = inquiryService.registerAnswer(principalOf(seller), 1L, new InquiryAnswerRequest("답변드립니다"));

        assertTrue(response.answered());
        assertEquals("답변드립니다", response.answerContent());
    }

    @Test
    @DisplayName("updateAnswer: 답변이 없으면 ANSWER_NOT_FOUND")
    void updateAnswer_answerNotFound() {
        Inquiry inquiry = new Inquiry(product, buyer, "원래 문의");
        when(inquiryRepository.findById(1L)).thenReturn(Optional.of(inquiry));
        when(product.getSeller()).thenReturn(seller);
        when(seller.getId()).thenReturn(10L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> inquiryService.updateAnswer(principalOf(seller), 1L, new InquiryAnswerRequest("수정 시도")));

        assertEquals("ANSWER_NOT_FOUND", exception.getErrorCode().name());
    }

    @Test
    @DisplayName("updateAnswer: 판매자 본인이고 답변이 있으면 내용이 수정된다")
    void updateAnswer_success() {
        Inquiry inquiry = new Inquiry(product, buyer, "원래 문의");
        inquiry.registerAnswer(seller, "기존 답변");
        when(inquiryRepository.findById(1L)).thenReturn(Optional.of(inquiry));
        when(product.getSeller()).thenReturn(seller);
        when(seller.getId()).thenReturn(10L);

        var response = inquiryService.updateAnswer(principalOf(seller), 1L, new InquiryAnswerRequest("수정된 답변"));

        assertEquals("수정된 답변", response.answerContent());
    }

    @Test
    @DisplayName("deleteAnswer: 답변이 없으면 ANSWER_NOT_FOUND")
    void deleteAnswer_answerNotFound() {
        Inquiry inquiry = new Inquiry(product, buyer, "원래 문의");
        when(inquiryRepository.findById(1L)).thenReturn(Optional.of(inquiry));
        when(product.getSeller()).thenReturn(seller);
        when(seller.getId()).thenReturn(10L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> inquiryService.deleteAnswer(principalOf(seller), 1L));

        assertEquals("ANSWER_NOT_FOUND", exception.getErrorCode().name());
    }

    @Test
    @DisplayName("deleteAnswer: 판매자 본인이 삭제하면 답변이 지워지고 문의는 남는다")
    void deleteAnswer_success() {
        Inquiry inquiry = new Inquiry(product, buyer, "원래 문의");
        inquiry.registerAnswer(seller, "기존 답변");
        when(inquiryRepository.findById(1L)).thenReturn(Optional.of(inquiry));
        when(product.getSeller()).thenReturn(seller);
        when(seller.getId()).thenReturn(10L);

        inquiryService.deleteAnswer(principalOf(seller), 1L);

        assertNull(inquiry.getAnswerContent());
        assertNull(inquiry.getAnsweredAt());
        assertEquals("원래 문의", inquiry.getContent());
    }
}
