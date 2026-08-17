# 카카오톡 공유하기 (상품 상세 페이지)

대상: share/kakao-share
담당: 민병준

## 배경 / 요구

"실제 공구 사이트처럼" 고도화 작업의 일환. 공동구매는 참여자가 모일수록 가격이 낮아지는 구조라, 상품 상세 페이지를 카카오톡으로 쉽게 공유할 수 있어야 실사용 관점에서 자연스럽다. 카카오 개발자 콘솔에서 JavaScript 키 발급 + 도메인(프로덕션/로컬) 등록 완료(선행 조건 해소).

## 설계

- 카카오 로그인(`docs/dev/auth/social-login/design.md`)은 서버가 `RestClient`로 직접 REST 호출하지만, 공유하기는 브라우저에서 실행되는 Kakao Share API라 서버가 대신할 수 없다 — 카카오 공식 JS SDK(CDN)를 그대로 쓴다(PortOne 브라우저 SDK를 `checkout.html`에서 CDN `<script>`로 쓴 기존 선례와 동일한 결).
- `product.html`에 카카오 JS SDK `<script>` 태그 추가 → `product.js`가 상품 상세 로드 후 `Kakao.init(jsKey)` 호출 → "카카오톡 공유하기" 버튼 클릭 시 `Kakao.Share.sendDefault()`로 상품명/가격/썸네일/현재 페이지 링크를 담은 카드 전송.
- JS 키는 `KAKAO_JS_KEY` 환경변수 → `kakao.js-key`(application.yaml) → `ProductService`가 `@Value`로 읽어 `ProductResponse.kakaoJsKey`에 실어 `GET /api/products/{productId}` 응답으로 내려준다(`PaymentResponse.portoneStoreId`/`portoneChannelKey`와 동일 패턴 — 별도 공용 config 엔드포인트 신설 없음).

## 태스크

- [ ] `application.yaml`에 `kakao.js-key` 추가
- [ ] `ProductResponse`에 `kakaoJsKey` 필드 추가, `ProductService.detail()`에서 전달
- [ ] `product.html` — 카카오 JS SDK `<script>` 태그 + "카카오톡 공유하기" 버튼
- [ ] `product.js` — `Kakao.init()`, 공유 버튼 클릭 핸들러(`Kakao.Share.sendDefault()`)
- [ ] `ProductControllerTest`/`ProductServiceTest`에 `kakaoJsKey` 포함 확인 케이스 추가
- [ ] `docs/api/product.md` — 응답 필드 추가 반영
- [ ] 로컬 bootRun + 실제 브라우저로 카카오톡 공유 실측(썸네일 있는 상품 / 없는 상품 둘 다)

## 평가(통과) 기준

- `./gradlew test` 통과, 회귀 없음
- 실제 브라우저에서 공유하기 버튼 → 카카오톡 공유 대화상자 → 실제 전송까지 확인
- 이미지 없는 상품도 공유 카드가 깨지지 않는지 확인
