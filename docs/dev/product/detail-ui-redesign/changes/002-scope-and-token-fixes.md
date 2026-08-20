# 상세 리디자인 후속 — 미정의 CSS 변수 4곳 + 공용 선택자 범위 누수 수정

대상: product/detail-ui-redesign
담당: 민병준 (전용운 커밋 `824fe91` 코드리뷰 후속)

## 배경

`824fe91`(상품 상세 2열 Split-Hero 리디자인) 코드리뷰 중 발견한 2건. 문서의 "기존 DOM ID 100% 보존" 주장은 `product.js`가 참조하는 ID 41개 + 이 페이지가 로드하는 다른 스크립트 7개까지 전수 대조해 **사실임을 확인**했다. 문제는 CSS 쪽에 있었다.

## 1) 정의되지 않은 CSS 변수 (4곳 + 기존 1곳)

| 선언 | 실제 동작 |
|---|---|
| `border: 1px solid var(--color-border-subtle)` (2곳) | `--color-border-subtle`가 `tokens.css`에 없음 → 선언이 invalid at computed-value time → `border-color`가 초기값 **`currentColor`(글자색)** 로 떨어져 의도한 연한 테두리 대신 진한 테두리 |
| `border-radius: var(--radius-xl)` (2곳) | `--radius-xl` 없음 → 초기값 **`0`** → 둥근 모서리가 아니라 각진 모서리 |
| `font-size: var(--fs-md)` (1곳, `.product-tab`) | `--fs-md` 없음(스케일이 xs/sm/base/lg/xl…) → font-size가 부모값으로 떨어짐. **이번 커밋 이전부터 있던 잠복 버그**(`09fc4c1`) — 같은 종류라 함께 수정 |

정의된 토큰으로 교체: `--color-border`, `--radius-lg`(12px), `--fs-base`(16px).

> 새 토큰을 만들지 않고 기존 스케일에 매핑한 이유 — 토큰 스케일은 의도적으로 고정돼 있고, 디자인 의도상 별도 값이 꼭 필요하면 `tokens.css`에 정식으로 추가하는 게 맞다(그건 디자인 결정이라 임의로 값을 만들지 않았다).

## 2) 공용 선택자 범위 누수 — 다른 3개 페이지가 함께 바뀌던 문제

리디자인이 기존 정의를 지우지 않고 파일 뒤쪽에 같은 선택자를 새로 추가해서, 선택자 4개가 중복 정의됐다(`.product-price-box`, `.price-tiers-table`, `.target-participants-options`, `.product-actions`). 뒤엣것이 이기지만 **앞에만 있는 속성은 살아남아 의도치 않게 병합**된다.

그런데 더 큰 문제는 **이 클래스들이 상품 상세 전용이 아니었다는 것**이다:

| 클래스 | 함께 쓰는 페이지 |
|---|---|
| `.product-actions` | `checkout.html`, `seller/products/new.html`, `seller/products/edit.html` |
| `.product-price-box` | `checkout.html` |
| `.price-tiers-table` | `checkout.html` |

리디자인이 `.product-actions`에 `flex-direction: column`을 넣으면서, **결제 페이지와 판매자 상품 등록/수정 페이지의 버튼이 나란히 놓이던 것에서 세로로 쌓이도록 함께 바뀌었다.** 상품 상세만 세로 배치가 의도였는데 나머지 3개 페이지가 휩쓸린 것.

리디자인 대상 요소가 전부 `.product-detail-summary` 안에 있으므로, 리디자인이 덮어쓴 선택자들을 **그 컨테이너 하위로 범위를 좁혔다**(`.product-detail-summary .product-actions { … }`). 상세 페이지의 새 디자인은 그대로 유지되고, 다른 페이지는 기존 공용 규칙으로 되돌아간다. 중복 선택자도 자연히 해소된다(전체 CSS 중복 선택자 0개 확인).

## 검증

- 정의되지 않은 `var()` 사용 **0건**(components/layout/base 전수 검사)
- 최상위 클래스 선택자 중복 **0건**
- 중괄호 균형 259:259
- 로컬 bootRun으로 실제 서빙 확인(범위 좁힌 규칙 적용, 공용 `.product-actions` 원본 유지), `product.html`/`checkout.html`/`seller/products/new.html` 전부 200
- `./gradlew test` **397케이스 통과**

## 배운 것

**클래스 이름이 페이지 전용처럼 보여도 실제 사용처를 먼저 확인해야 한다.** `.product-actions`는 이름만 보면 상품 상세 전용 같지만 결제·판매자 페이지가 같이 쓰고 있었다. 기존 선택자를 "덮어쓰는" 방식으로 리디자인하면 그 선택자를 쓰는 모든 페이지가 함께 바뀐다 — 한 페이지만 바꾸려면 그 페이지의 컨테이너로 범위를 좁혀야 한다.
