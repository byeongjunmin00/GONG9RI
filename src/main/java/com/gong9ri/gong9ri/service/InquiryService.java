package com.gong9ri.gong9ri.service;

import com.gong9ri.gong9ri.common.exception.BusinessException;
import com.gong9ri.gong9ri.common.exception.ErrorCode;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.dto.InquiryAnswerRequest;
import com.gong9ri.gong9ri.dto.InquiryCreateRequest;
import com.gong9ri.gong9ri.dto.InquiryListResponse;
import com.gong9ri.gong9ri.dto.InquiryResponse;
import com.gong9ri.gong9ri.entity.Inquiry;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Product;
import com.gong9ri.gong9ri.repository.InquiryRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상품 문의 작성/목록/수정/삭제 + 판매자 답변 등록/수정/삭제 (docs/dev/ongoing/product-inquiry.md,
 * docs/api/inquiry.md).
 *
 * <p>리뷰와 달리 구매 이력을 요구하지 않는다(로그인한 회원이면 role과 무관하게 문의 작성 가능). 답변이
 * 등록된 문의는 작성자가 더 이상 손댈 수 없다(질문-답변 정합성 보존) — 그 상품을 등록한 판매자 본인만
 * 답변을 등록·수정·삭제할 수 있고, 답변 삭제는 문의 자체는 남기고 "미답변" 상태로 되돌린다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InquiryService {

    private final InquiryRepository inquiryRepository;
    private final ProductRepository productRepository;

    public InquiryListResponse list(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        return InquiryListResponse.of(inquiryRepository.findByProductIdOrderByCreatedAtDesc(productId));
    }

    @Transactional
    public InquiryResponse create(MemberUserDetails principal, Long productId, InquiryCreateRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        Member member = principal.getMember();

        Inquiry saved = inquiryRepository.save(new Inquiry(product, member, request.content()));
        log.info("문의 작성 완료: inquiryId={}, productId={}, memberId={}", saved.getId(), productId, member.getId());
        return InquiryResponse.from(saved);
    }

    @Transactional
    public InquiryResponse update(MemberUserDetails principal, Long inquiryId, InquiryCreateRequest request) {
        Inquiry inquiry = getInquiryOrThrow(inquiryId);
        requireOwner(principal, inquiry);
        requireNotAnswered(inquiry);

        inquiry.updateContent(request.content());
        log.info("문의 수정 완료: inquiryId={}, memberId={}", inquiryId, principal.getMember().getId());
        return InquiryResponse.from(inquiry);
    }

    @Transactional
    public void delete(MemberUserDetails principal, Long inquiryId) {
        Inquiry inquiry = getInquiryOrThrow(inquiryId);
        requireOwner(principal, inquiry);
        requireNotAnswered(inquiry);

        inquiryRepository.delete(inquiry);
        log.info("문의 삭제 완료: inquiryId={}, memberId={}", inquiryId, principal.getMember().getId());
    }

    @Transactional
    public InquiryResponse registerAnswer(MemberUserDetails principal, Long inquiryId, InquiryAnswerRequest request) {
        Inquiry inquiry = getInquiryOrThrow(inquiryId);
        requireSeller(principal, inquiry);
        if (inquiry.isAnswered()) {
            throw new BusinessException(ErrorCode.INQUIRY_ALREADY_ANSWERED);
        }

        inquiry.registerAnswer(principal.getMember(), request.content());
        log.info("문의 답변 등록 완료: inquiryId={}, sellerId={}", inquiryId, principal.getMember().getId());
        return InquiryResponse.from(inquiry);
    }

    @Transactional
    public InquiryResponse updateAnswer(MemberUserDetails principal, Long inquiryId, InquiryAnswerRequest request) {
        Inquiry inquiry = getInquiryOrThrow(inquiryId);
        requireSeller(principal, inquiry);
        requireAnswered(inquiry);

        inquiry.updateAnswer(request.content());
        log.info("문의 답변 수정 완료: inquiryId={}, sellerId={}", inquiryId, principal.getMember().getId());
        return InquiryResponse.from(inquiry);
    }

    @Transactional
    public void deleteAnswer(MemberUserDetails principal, Long inquiryId) {
        Inquiry inquiry = getInquiryOrThrow(inquiryId);
        requireSeller(principal, inquiry);
        requireAnswered(inquiry);

        inquiry.deleteAnswer();
        log.info("문의 답변 삭제 완료: inquiryId={}, sellerId={}", inquiryId, principal.getMember().getId());
    }

    private Inquiry getInquiryOrThrow(Long inquiryId) {
        return inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INQUIRY_NOT_FOUND));
    }

    private void requireOwner(MemberUserDetails principal, Inquiry inquiry) {
        if (!inquiry.getMember().getId().equals(principal.getMember().getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    // 그 문의가 달린 상품을 등록한 판매자 본인인지 확인한다(구매자 계정, 다른 상품의 판매자 모두 거절).
    private void requireSeller(MemberUserDetails principal, Inquiry inquiry) {
        if (!inquiry.getProduct().getSeller().getId().equals(principal.getMember().getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void requireNotAnswered(Inquiry inquiry) {
        if (inquiry.isAnswered()) {
            throw new BusinessException(ErrorCode.INQUIRY_ALREADY_ANSWERED);
        }
    }

    private void requireAnswered(Inquiry inquiry) {
        if (!inquiry.isAnswered()) {
            throw new BusinessException(ErrorCode.ANSWER_NOT_FOUND);
        }
    }
}
