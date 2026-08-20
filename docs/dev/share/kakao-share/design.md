# 카카오톡 공유하기 — Design

## 개요

상품 상세 페이지(`product.html`)에서 "카카오톡 공유하기" 버튼을 누르면 카카오 JS SDK(CDN)의 Kakao Share 기본 템플릿으로 상품명·가격·썸네일·현재 페이지 링크(`window.location.href`)를 담은 카드를 카카오톡 공유 대화상자로 전송한다.

카카오 로그인(`docs/dev/auth/social-login/design.md`)은 서버가 `RestClient`로 직접 REST 호출하지만, 공유하기는 브라우저에서 실행되는 Kakao Share API라 서버가 대신할 수 없다 — 카카오 공식 JS SDK를 CDN `<script>` 태그로 그대로 쓴다(`checkout.html`의 PortOne 브라우저 SDK와 동일한 결).

## API / 인터페이스

`GET /api/products/{productId}` 응답에 `kakaoJsKey` 필드가 함께 내려온다(`docs/api/product.md`). 서버 설정(`kakao.js-key` ← `KAKAO_JS_KEY` 환경변수)이 비어있으면 빈 문자열이 내려오고, 이 경우 프론트는 공유 버튼을 계속 숨긴 채로 둔다. 도메인 화이트리스트(카카오 콘솔에 등록한 사이트 도메인)로 보호되는 공개 가능한 값이라 별도 인증 없이 내려준다 — 결제 생성 응답에 `portoneStoreId`/`portoneChannelKey`를 실어 보내는 기존 패턴(`docs/api/payment.md`)과 동일하다.

## 데이터 모델

새 테이블/컬럼 없음. `kakao.js-key`는 DB가 아니라 서버 설정값(환경변수)이다.

## 규칙 / 검증

- `product.js`가 상품 상세를 불러온 뒤(`renderProduct`) `kakaoJsKey`가 있으면 `Kakao.init(...)`으로 SDK를 초기화하고 공유 버튼을 노출한다. 키가 없으면(로컬에서 `KAKAO_JS_KEY` 미설정 등) 버튼을 숨긴 채로 두어 초기화 없이 공유를 시도하는 에러를 원천 차단한다.
- 상품에 등록된 이미지(`imageUrl`)가 있으면 `feed` 템플릿(제목/설명/썸네일/버튼)을, 없으면 `text` 템플릿(제목+설명을 합친 텍스트/링크/버튼)을 쓴다 — 카카오 SDK상 `feed` 템플릿은 `imageUrl`이 필수라서, 썸네일 없는 상품도 카드가 깨지지 않게 분기했다.
- 공유 카드 썸네일은 절대경로(`https://...`) 이미지 URL이어야 카카오 서버가 접근해 노출한다(카카오 측 제약). `imageUrl`은 판매자가 상품 등록 폼에 직접 입력하는 URL 텍스트 필드라(별도 업로드 기능 없음) 로컬 파일 경로나 `localhost` 주소를 넣으면 썸네일이 안 뜰 수 있음 — 별도 방어 로직 없이 그대로 카카오 SDK에 맡긴다.
- 공유 링크는 **서버가 내려주는 `shareUrl`**(상세 응답 필드, `app.base-url` + `/product.html?id={id}`)을 쓴다. `shareUrl`이 없으면(이 필드 추가 이전에 캐시된 응답) `window.location.href`로 폴백한다.
  - 원래는 `window.location.href`를 그대로 썼는데, 그러면 **공유한 사람이 보고 있던 주소**가 그대로 나간다 — 로컬(`localhost:8080`)에서 공유하면 받는 사람 기기의 localhost를 찾다가 아무것도 안 열리고(2026-08-17 실사용 확인), 추적용 쿼리파라미터가 붙어 있으면 그것까지 딸려간다. **공유 링크는 어디서 눌렀든 공개 주소여야 한다**는 판단으로 2026-08-20에 서버가 주는 값으로 바꿨다.
### 카카오 콘솔 설정 — 등록소가 **두 개**다 (2026-08-20 실사용에서 발견)

이 둘을 같은 것으로 착각하면 **메시지는 가는데 링크만 죽는** 상태가 된다. 실제로 그렇게 됐다.

| 콘솔 위치 | 무엇을 허용하나 | 안 하면 |
|---|---|---|
| 앱 설정 > 플랫폼 키 > **JavaScript 키 > JavaScript SDK 도메인** | 그 도메인에서 **JS SDK를 쓸 수 있게** | 공유 자체가 안 됨 |
| 앱 > **제품 링크 관리 > 웹 도메인** | 카드에서 **링크 이동을 허용할** 도메인 | 카드는 정상 전송되는데 **링크·버튼이 통째로 제거됨** |

> 콘솔 개편으로 예전 "플랫폼 > Web > 사이트 도메인"이 위 두 곳으로 쪼개졌다(로그인 Redirect URI가 REST API 키 상세로 들어간 것과 같은 패턴).

**증상과 진단 (2026-08-20)**: "카톡은 오는데 눌러도 무반응, PC 카톡은 '모바일에서 확인'". JS SDK 도메인은 등록돼 있어 메시지는 전송됐지만, **제품 링크 관리 > 웹 도메인이 비어 있어** 카카오가 링크를 떼어낸 상태였다. 결정적 단서는 **받은 카드에 `상품 보러가기` 버튼이 없다**는 것 — 이미지·제목·설명은 살아있는데 링크만 사라진 게 보였다. 웹 도메인 등록 후 정상 동작 확인.

> 콘솔 설정을 의심할 때는 **받은 카드 자체를 먼저 볼 것.** 버튼 유무가 "보내기 실패"와 "링크 미승인"을 바로 갈라준다.
- 관련 코드: `product.html`(SDK `<script>` 태그, `#kakao-share-btn`), `js/product.js`(`setUpKakaoShare`, `handleKakaoShare`), `ProductResponse.kakaoJsKey`, `ProductService`(`@Value("${kakao.js-key}")`), `application.yaml`(`kakao.js-key`).
