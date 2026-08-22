# todo-backlog — 누적 할일 백로그

에이전트가 계속 작업 중일 때 컨텍스트/토큰을 아끼기 위해 중간에 지시를 끼워넣지 않고,
"투두작업에 추가해" / "할일목록에 적어놔" 같은 요청이 오면 여기에 번호를 이어서 추가하는 용도의 문서다.

- 번호는 등록 순서일 뿐, 우선순위나 작업 단위를 뜻하지 않는다. 한 번에 여러 개를 묶어서 하거나, 하나를 여러 개로 쪼개서 진행해도 된다.
- 실제로 작업을 시작할 항목은 AGENTS.md의 Plan → 휴먼게이트 → Generate → Evaluate 절차를 그대로 따른다 (`docs/dev/ongoing/{작업}.md` 계획 문서 작성 → 승인 → 구현 → 평가).
- 완료된 항목은 체크 표시하고, 해당 기능의 `changes/` 문서로 연결 링크를 남긴다.

## 목록

1. [x] 리뷰·문의 날짜/시간 표기 → [docs/dev/frontend/product-detail/changes/003-ui-polish.md](frontend/product-detail/changes/003-ui-polish.md)
2. [x] 리뷰·문의 디자인 변경 (박스 제거) → [docs/dev/frontend/product-detail/changes/003-ui-polish.md](frontend/product-detail/changes/003-ui-polish.md)
3. [x] 리뷰·문의 작성 박스 위치 조정 (리뷰·문의 목록보다 상단으로) → [docs/dev/frontend/product-detail/changes/003-ui-polish.md](frontend/product-detail/changes/003-ui-polish.md)
4. [x] 관리자 상담 관리: 회원 카드와 상담 삭제 사이 구분선 제거 + 상담 삭제 버튼을 메타 정보에 맞춰 한 줄로 배치. 그에 따라 카드 상하 폭 축소 → `docs/dev/admin/changes/013-admin-support-room-card-layout.md`
5. [x] 제품 상세 페이지의 제품 사진 쪽에도 찜하기 기능 추가 → [docs/dev/frontend/product-detail/changes/003-ui-polish.md](frontend/product-detail/changes/003-ui-polish.md)
6. [x] 신설팀 목표 인원 선택 토글: 첫 번째 토글 자동 선택 (토글이 하나뿐이면 클릭 안 해도 되게, 여러 개여도 첫 번째가 기본 체크되게) → [docs/dev/frontend/product-detail/changes/003-ui-polish.md](frontend/product-detail/changes/003-ui-polish.md)
7. [x] 판매자·구매자가 제품 상세 페이지에서 구매할 수 없는 상황(자기 상품/자기 화면)에서도 "공구팀 신설", "구매하기", "계속 쇼핑하기" 버튼이 보이는 문제. 또한 공구팀이 정원을 채워 성사(SUCCESS)된 이후에는 어떤 경우에도 환불이 불가능하다는 내용을 확인했다는 체크란도 이런 상황에 그대로 보이는 문제 — 안 보이게 하고, 그에 맞춰 서머리 영역 크기도 조정 → [docs/dev/product/purchase-visibility/changes/001-purchase-visibility.md](product/purchase-visibility/changes/001-purchase-visibility.md)
8. [x] 7번과 별개로, 구매자에게만 해당하는 기능/문구인데 판매자나 관리자 화면에서도 똑같이 노출되는 문제를 전체적으로 점검. 문구 수정 또는 조건부 숨김 처리, 반대 상황(판매자/관리자 전용인데 구매자 화면에 노출)도 함께 파악해서 수정 → [docs/dev/product/purchase-visibility/changes/001-purchase-visibility.md](product/purchase-visibility/changes/001-purchase-visibility.md)
