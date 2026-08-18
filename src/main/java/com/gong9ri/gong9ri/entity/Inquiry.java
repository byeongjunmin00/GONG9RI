package com.gong9ri.gong9ri.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

// 상품 문의 — 리뷰와 달리 구매 이력을 요구하지 않는다(구매 전 질문이 핵심 용도, docs/db/inquiry.md).
// 문의 1건당 답변은 0개 또는 1개(스레드형 다중 답변 없음)이고, 그 상품을 등록한 판매자(product.seller)
// 본인만 답변을 등록·수정·삭제할 수 있다(InquiryService에서 검증, 이 엔티티 자체는 그 전제를 강제하지
// 않는다). 답변이 등록되면 작성자는 더 이상 문의 내용을 수정/삭제할 수 없다(InquiryService에서 검증).
@Entity
@Table(name = "inquiry", indexes = {
        @Index(name = "idx_product", columnList = "product_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Inquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    // NULL이면 미답변. 답변 삭제 시 이 값과 answeredBy/answeredAt을 함께 NULL로 되돌려 "미답변" 상태로
    // 복귀한다(문의 자체는 남긴다, docs/db/inquiry.md "삭제 정책").
    @Column(columnDefinition = "TEXT")
    private String answerContent;

    // 답변한 판매자. 항상 product.seller와 같아야 한다(서비스 레이어에서 검증, 이 엔티티 자체가
    // 강제하지는 않는다).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answered_by")
    private Member answeredBy;

    // 답변 등록 시각. 미답변인 동안은 NULL. 답변 수정 시에는 바뀌지 않는다("등록" 시각이라는 의미를
    // 그대로 유지, docs/db/inquiry.md).
    private LocalDateTime answeredAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Inquiry(Product product, Member member, String content) {
        this.product = product;
        this.member = member;
        this.content = content;
    }

    public boolean isAnswered() {
        return this.answerContent != null;
    }

    public void updateContent(String content) {
        this.content = content;
    }

    public void registerAnswer(Member answeredBy, String answerContent) {
        this.answeredBy = answeredBy;
        this.answerContent = answerContent;
        this.answeredAt = LocalDateTime.now();
    }

    public void updateAnswer(String answerContent) {
        this.answerContent = answerContent;
    }

    // 답변만 지우고 문의(질문)는 남겨 "미답변" 상태로 되돌린다.
    public void deleteAnswer() {
        this.answerContent = null;
        this.answeredBy = null;
        this.answeredAt = null;
    }
}
