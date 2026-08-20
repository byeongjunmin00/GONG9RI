# 썸네일 sticky가 하단 섹션을 덮던 버그 수정

대상: product/detail-ui-redesign
담당: 민병준

## 배경 — 사용자 리포트

> "스크롤 내리면 사진이 따라와서 다 가리네 이거 버그인 거 같아"

상품 상세 페이지에서 스크롤을 내리면 좌측 썸네일이 계속 따라 내려와 **"모집 중인 공구팀" 섹션을 덮었다.** 스크린샷에서 제목 글자가 이미지에 가려 "모ㅈ ㅅ이 공구팀"처럼 보였다.

## 원인

리디자인(`824fe91`)이 만든 그리드에 **네 개**가 들어 있었다.

| 그리드 자식 | 배치 |
|---|---|
| `.product-detail-media` (`position: sticky`) | 1행 1열 |
| `.product-detail-summary` | 1행 2열 |
| `.team-list-section.product-detail-wide` | 2행 (`grid-column: 1 / -1`) |
| `.product-tabs-section.product-detail-wide` | 3행 (동일) |

즉 **전폭 섹션을 그리드 안에 넣어놓고 `grid-column: 1 / -1`로 다시 전폭으로 되돌리는** 구조였다. 그 결과 sticky 썸네일이 아래 행 영역까지 따라 내려와 콘텐츠를 덮었다.

## 수정

전폭 섹션을 **그리드 밖 형제로** 빼고, 상단 2열만 별도 래퍼(`.product-detail-grid`)로 묶었다.

```
#product-detail  (flex column, gap)
├── .product-detail-grid   ← 2열 그리드는 여기까지
│   ├── .product-detail-media    (sticky)
│   └── .product-detail-summary
├── .team-list-section
└── .product-tabs-section
```

이러면 sticky의 컨테이닝 블록이 상단 래퍼로 한정돼 **구조적으로 아래 섹션을 넘어갈 수 없다.** CSS 값을 조정해 증상을 누르는 게 아니라 원인이 되는 구조를 없앤 것이다.

`.product-detail-wide`(`grid-column: 1 / -1` + `margin-top`)는 함께 제거했다 — 그리드 밖으로 나왔으니 되돌릴 그리드가 없고, 섹션 간 간격은 부모 `.product-detail`의 `flex column` + `gap`이 이미 담당한다.

`#product-detail`은 JS가 `hidden`으로 토글하는 대상이라 전폭 섹션도 **그 안에는 남아 있어야** 한다. 그래서 완전히 밖으로 빼지 않고 그리드 래퍼를 안쪽에 새로 두는 형태를 택했다.

## 검증

마크업을 옮겼으므로 참조가 깨지지 않았는지 전수 확인했다.

- `product.js`가 참조하는 **ID 41개 전부 존재**(누락 0)
- 클래스 선택자 4개 중 `team-item-join-btn`만 HTML에 없는데, 이는 **JS가 팀 목록을 그릴 때 동적으로 붙이는 클래스**(`product.js:610`)이고 수정 전 원본에도 HTML엔 없었다 — 이번 변경과 무관함을 확인
- HTML 태그 균형(div 35:35, section/ul/table/form 전부 일치), CSS 중괄호 263:263
- 로컬 실서버 서빙 확인 — 그리드 래퍼 존재, `product-detail-wide` 잔여 0(HTML·CSS 양쪽), sticky 규칙은 유지, 페이지 200
- `./gradlew test` **407케이스 통과**

> **육안 확인은 사용자 몫이다.** 이 저장소 작업 환경엔 브라우저가 없어 실제 렌더링(스크롤 시 겹침이 사라졌는지)은 검증할 수 없다. 구조적으로 겹칠 수 없게 만들었고 서빙까지 확인했으나, 최종 확인은 사용자가 해야 한다.

## 배운 것

**"그리드에 넣고 다시 그리드를 무효화한다"는 구조 자체가 신호였다.** `grid-column: 1 / -1`로 전폭을 되돌리는 요소가 여럿이면, 그건 애초에 그리드 밖에 있어야 할 것들이라는 뜻이다. 그 부자연스러움이 sticky 겹침이라는 눈에 보이는 증상으로 드러났다.
