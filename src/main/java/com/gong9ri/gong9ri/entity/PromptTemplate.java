package com.gong9ri.gong9ri.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 판매자 상품등록 AI 도우미의 프롬프트 템플릿 — 자바 코드에 하드코딩하지 않고 DB에 저장해서,
 * 내용을 바꾸고 싶으면 재배포 없이 이 테이블 값만 UPDATE하면 되게 한다
 * (docs/dev/ai/product-suggestion/design.md). {@code content}에는 판매자 입력 텍스트가 들어갈
 * {@code {input}} 플레이스홀더가 포함돼 있다.
 */
@Entity
@Table(name = "prompt_template", uniqueConstraints = {
        @UniqueConstraint(name = "uk_category", columnNames = "category")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class PromptTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PromptCategory category;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // 프롬프트를 고칠 때마다 1씩 올린다 — 개선 이력(docs/logs/ai/product-suggestion/001-product-suggestion.md)을
    // 어느 버전 시점의 결과인지와 대조할 수 있게 한다.
    @Column(nullable = false)
    private Integer version;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public PromptTemplate(PromptCategory category, String content, Integer version) {
        this.category = category;
        this.content = content;
        this.version = version;
    }
}
