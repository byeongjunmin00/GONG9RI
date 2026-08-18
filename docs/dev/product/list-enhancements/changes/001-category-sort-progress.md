# 상품 목록 고도화 — 카테고리·정렬·참여 진행바

대상: product/list-enhancements
담당: 민병준

## 배경 / 요구

친구 피드백("메인 화면에 가로로 긴 광고 배너 + 그 아래 카테고리")을 계기로 카테고리 기능을 실제로 만들기로 함. 이어서 친구가 추천한 목업(카테고리 바 옆 "인기순" 정렬, 카드에 참여 인원 진행바)을 보고 "인기순도 참여자수로 만들자"는 사용자 요청으로 정렬·진행바까지 확장.

## 설계

- `docs/dev/product/list-enhancements/design.md` 참고 — 카테고리 필터, LATEST/POPULAR 정렬(DB 레벨 서브쿼리), 참여 진행바(캐시 밖에서 별도 계산) 3가지를 한 번에 진행.
- 진행바 대표 팀 선택 기준(달성률 최고 vs 참여자 최다 vs 팀이 정확히 1개일 때만 표시)은 AskUserQuestion으로 사용자 확인 후 "달성률 최고"로 확정. 인기순 정렬 기준은 별도로 "참여자 최다"로 결정(사용자 명시 요청).

## 태스크

- [x] `ProductCategory` enum + `Product.category` 필드(+ 마이그레이션 `@ColumnDefault`)
- [x] DTO(`ProductRegisterRequest`/`Response`/`SummaryResponse`) category 반영
- [x] Repository 카테고리 필터 + 캐시 키 반영
- [x] `ProductSort`(LATEST/POPULAR) + 상관 서브쿼리 정렬 + 캐시 키 반영
- [x] `attachActiveTeamProgress()` — 캐시 없는 진행바 계산, self-invocation 문제 회피
- [x] 상품 등록/수정 폼에 카테고리 select 추가
- [x] 테스트: 카테고리 필터, 카테고리 필수 검증, 인기순 정렬, 진행바(대표 팀 선택 + 캐시 상태에서도 최신 반영) — `ProductControllerTest`
- [x] `docs/api/product.md`/`docs/db/product.md` 갱신

## 평가(통과) 기준

- `./gradlew test` 전체 통과(회귀 없음)
- 로컬 실서버로 카테고리 필터링, 인기순/최신순 정렬 순서, 진행바 숫자(캐시된 페이지에서도 팀 참가 직후 최신 반영) 전부 실측 확인
- 사용자가 실제 브라우저에서 진행바·정렬 동작 확인 완료
