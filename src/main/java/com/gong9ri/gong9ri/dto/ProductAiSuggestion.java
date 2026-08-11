package com.gong9ri.gong9ri.dto;

/**
 * LLM 응답이 파싱되는 구조화 출력 타입(Spring AI {@code BeanOutputConverter}가 이 record의 필드명을
 * 기준으로 JSON Schema를 만들어 프롬프트에 포맷 지시문으로 넣고, 응답 JSON을 다시 이 타입으로 파싱한다).
 * 판매자 최종 검토 전 "제안"일 뿐이라 이 값 그대로 DB에 상품이 생성되지 않는다 — 판매자가 확인 후
 * 기존 {@code POST /api/products}로 직접 등록한다.
 */
public record ProductAiSuggestion(
        String suggestedName,
        String suggestedDescription,
        Integer suggestedBasePrice,
        Integer suggestedMaxParticipants
) {
}
