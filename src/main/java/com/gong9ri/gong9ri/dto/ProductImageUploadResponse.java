package com.gong9ri.gong9ri.dto;

/**
 * 이미지 업로드 결과 — 저장된 파일을 가리키는 앱 내부 경로({@code /uploads/...}).
 * 이 값을 상품 등록/수정 요청의 이미지 목록에 그대로 넣으면 된다(외부 URL과 동일하게 취급된다).
 */
public record ProductImageUploadResponse(String url) {
}
