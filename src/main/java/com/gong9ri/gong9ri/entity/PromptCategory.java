package com.gong9ri.gong9ri.entity;

/**
 * 판매자 상품등록 AI 도우미의 프롬프트 템플릿 분기 기준(docs/dev/ai/product-suggestion/design.md).
 * FOOD는 신선식품(유통기한·중량·신선도 강조), GENERAL은 그 외 일반 상품(스펙·소재·사이즈 강조).
 */
public enum PromptCategory {
    FOOD,
    GENERAL
}
