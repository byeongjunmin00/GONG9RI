# 로그아웃 (auth/logout)

대상: auth/logout
담당: 전용운

## 배경 / 요구

`docs/api/auth.md`에 `POST /api/auth/logout`(요청 body 없음, 서버측 `HttpSession.invalidate()`, 성공 `204`, 실패 `401 UNAUTHORIZED`)이 문서화되어 있지만, `src/main/java` 전체에 `logout` 매핑이 존재하지 않는다(`AuthController` 등 미구현). 실제로 호출하면 Spring이 정적 리소스 요청으로 오인해 `500`(`NoResourceFoundException: No static resource api/auth/logout`)을 반환한다 — 문서(계약)와 실제 코드가 불일치하는 상태를 해소한다.

## 설계

- **인가 규칙**: 현재 `SecurityConfig`는 `/api/auth/**` 전체를 `permitAll`로 열어두고 있다(`docs/dev/auth/login/design.md`에도 "다음 기능이 생기면 재검토 필요"로 명시된 리스크). `logout`은 인증된 사용자만 의미가 있고, 미인증 시 `401 UNAUTHORIZED`를 계약대로 반환해야 하므로, `permitAll` 대상을 `signup`/`login`으로 좁히고 `logout`은 `anyRequest().authenticated()` 규칙에 걸리게 한다. 이렇게 하면 미인증 요청은 기존 `ApiAuthenticationEntryPoint`가 이미 처리하는 401 흐름을 그대로 재사용할 수 있다(컨트롤러에서 별도 인증 여부 분기 불필요).
- **컨트롤러**: `AuthController`에 `POST /api/auth/logout`을 추가한다. 인증된 요청만 도달하므로, 세션 무효화 후 `204 No Content`를 반환하는 방향으로 구현한다(세션·SecurityContext 정리 방식의 구체 API 선택은 Generate 단계에서 결정).
- 새 `ErrorCode`는 필요 없다 — `UNAUTHORIZED`가 이미 존재한다(`common/exception/ErrorCode.java`).
- 영향 계층: `config`(SecurityConfig 인가 규칙) · `controller`(AuthController).

## 태스크

- [ ] `SecurityConfig`: `/api/auth/**` permitAll 범위를 signup/login으로 좁혀 logout이 인증 필요 규칙에 들어가게 조정
- [ ] `AuthController.logout()` 구현 (세션 무효화 → 204)
- [ ] `AuthControllerTest`에 로그아웃 테스트 추가 (인증 상태 → 204 + 세션 무효화 확인, 미인증 상태 → 401 UNAUTHORIZED)

## 평가(통과) 기준

- `./gradlew test` 전체 통과 (신규 로그아웃 테스트 포함, 기존 signup/login 테스트 회귀 없음)
- 계약 준수 확인 (`docs/api/auth.md`):
  - 로그인된 세션으로 `POST /api/auth/logout` → `204 No Content`, 이후 세션이 무효화되어 있음
  - 미인증 상태로 `POST /api/auth/logout` → `401`, `code: UNAUTHORIZED`
  - (회귀) `POST /api/auth/signup`, `POST /api/auth/login`은 계속 인증 없이 호출 가능

## 리스크 / 전제

- `SecurityConfig`의 `/api/auth/**` permitAll 범위를 좁히는 변경이라, 현재 그 규칙에 걸리는 다른 경로가 없는지 확인이 필요하다(현재 `/api/auth/**` 하위엔 signup/login만 존재하므로 영향 낮음으로 판단됨).
- 브랜치 전략은 `docs/branch-guide.md`(단일 `main`, 브랜치 분기 없음)를 따른다 — `AGENTS.md` 본문의 `feature/*` 언급은 이 문서와 배치되는 구버전 서술로 보이며, 상세 문서인 `branch-guide.md`를 우선한다.
