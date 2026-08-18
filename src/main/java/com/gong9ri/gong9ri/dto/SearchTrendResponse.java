package com.gong9ri.gong9ri.dto;

import java.util.List;

// 실시간 인기 검색어(product/search-trends) — 순위 순서로 나열된 검색어 목록. 와디즈 참고 화면처럼
// 검색 횟수 숫자는 굳이 보여주지 않고 순위만 노출한다(프론트에서 리스트 인덱스로 번호를 매긴다).
public record SearchTrendResponse(List<String> keywords) {

    public static SearchTrendResponse of(List<String> keywords) {
        return new SearchTrendResponse(keywords);
    }
}
