# 로그인 엔터 키 지원

대상: auth/login
담당: 전용운

## 배경 / 요구

- 현재 로그인 화면(`login.html`, `admin/login.html`)에서 사용자가 아이디나 비밀번호를 입력한 후 Enter 키를 눌렀을 때 편하게 로그인이 진행되지 않고, 마우스로 직접 "로그인" 버튼을 클릭해야 하는 불편함이 있다.
- 키보드 조작 편의성을 높이기 위해 아이디/비밀번호 입력 필드에서 Enter 키 입력 시 직관적으로 다음 단계 이동 또는 로그인이 실행되도록 개선한다.

## 설계

### 키보드 인터랙션 흐름
1. **아이디(`username`) 입력 필드에서 Enter 입력 시**:
   - 비밀번호(`password`) 필드가 비어 있으면 비밀번호 입력 필드로 포커스 이동 (`passwordInput.focus()`).
   - 비밀번호(`password`) 필드에 이미 값이 채워져 있으면 바로 로그인 폼 제출 실행.
2. **비밀번호(`password`) 입력 필드에서 Enter 입력 시**:
   - 즉시 로그인 폼 제출 실행.
3. **IME 조합 고려**:
   - 한글 입력기 조합 중 Enter 입력 시 중복 이벤트 발생을 방지하기 위해 `event.isComposing` 상태 체크.
4. **일반 로그인 및 관리자 로그인 일관성 유지**:
   - `src/main/resources/static/js/login.js` (일반 사용자 로그인)
   - `src/main/resources/static/js/admin-login.js` (관리자 로그인)
   - 두 로그인 화면 모두 동일한 엔터 키 UX 적용.

## 태스크

- [x] `src/main/resources/static/js/login.js`에 Enter 키 keydown 이벤트 핸들러 추가
  - `username` 필드: 비어있는 비밀번호로 포커스 이동 또는 로그인 시도
  - `password` 필드: 로그인 시도
- [x] `src/main/resources/static/js/admin-login.js`에 동일한 Enter 키 인터랙션 적용
- [x] Evaluate 단계: 스코프 테스트 통과 확인 (`./gradlew test --tests "*Auth*" --tests "*Login*"`)
- [x] Evaluate 단계: `docs/dev/auth/login/design.md` 갱신 및 `ongoing/login-enter-key.md` → `changes/003-login-enter-key.md` 채번 이동

## 평가(통과) 기준

1. `login.html`에서 아이디 입력 후 Enter 누르면 비밀번호 창으로 포커스가 이동한다 (비밀번호가 비어있을 때).
2. 아이디와 비밀번호가 모두 입력된 상태에서 아이디 또는 비밀번호 입력창에서 Enter를 누르면 로그인 요청이 전송된다.
3. `admin/login.html`에서도 동일하게 Enter 키를 통한 포커스 이동 및 로그인이 정상 작동한다.
4. 기존 마우스 클릭을 통한 로그인 및 유효성 검증, 에러 메시지 표시 기능이 영향 없이 정상 동작한다.
