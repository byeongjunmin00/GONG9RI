package com.gong9ri.gong9ri.dto;

// GET /api/products의 정렬 옵션(product/list-sort). 값이 없으면(query param 생략) 정렬 조건 없이
// 기존과 동일하게 DB 자연 순서로 반환한다(회귀 방지 — 기존 호출부와 동일하게 동작).
public enum ProductSort {
    LATEST,
    POPULAR,
    DEADLINE
}
