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
- 카카오 콘솔의 **JavaScript 키 > JS SDK 도메인**에 공유가 일어나는 도메인이 등록돼 있어야 한다(콘솔 개편으로 "플랫폼 > Web"이 키별로 쪼개져 이 위치로 옮겨졌다 — 로그인 Redirect URI가 REST API 키 안으로 들어간 것과 같은 패턴). 프로덕션·`http://localhost:8080` 둘 다 등록돼 있음(2026-08-20 확인).
- 관련 코드: `product.html`(SDK `<script>` 태그, `#kakao-share-btn`), `js/product.js`(`setUpKakaoShare`, `handleKakaoShare`), `ProductResponse.kakaoJsKey`, `ProductService`(`@Value("${kakao.js-key}")`), `application.yaml`(`kakao.js-key`).
