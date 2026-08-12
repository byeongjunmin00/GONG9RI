# 배포 고도화 — 파이프라인 구멍 메우기 (cd/deploy)

대상: cd/deploy
담당: 민병준

## 배경 / 요구

발제 백엔드 도전과제 "배포 고도화". 오늘 실제로 겪은 프로덕션 502 전체 다운 사고(전용운 `8b39136`, `docs/logs/ai/policy-rag/002-boot-decoupling.md`)가 실제 근거 — 그 커밋은 이번 사고의 증상만 없앴고, "부팅 중 예외가 배포 전체를 끌고 내려가는" 구조적 위험은 남아있음.

전용운이 배포 파이프라인 구멍 6개(CI 게이팅/헬스체크 미설정/무중단 배포 미실측/Flyway 도입 여부/롤백 런북 없음/컨테이너 메모리 설정)를 정리해줬고, 논의 후 스코프를 좁힘:
- 이번에 함: CI 게이팅, 헬스체크 설정, 무중단 배포 실측, 롤백 런북
- 검증만 하고 필요할 때만 손댐: 컨테이너 메모리
- 이번엔 안 함: Flyway 도입 — 이미 design.md에 스코프 밖으로 명시돼 있어 재작업 없음(위 "재확인" 항목 참고)

## 설계

`docs/dev/cd/deploy/design.md`의 "배포 고도화" 절 참고.

## 태스크

- [x] `application.yaml`에 `management.endpoints.web.exposure.include: health` 추가
- [x] `SecurityConfig`에 `/actuator/health` permitAll 추가
- [x] `railway.json` 신규(healthcheckPath/healthcheckTimeout)
- [x] 로컬에서 `/actuator/health`는 200, 다른 `/actuator/**`는 여전히 401인지 실측
- [x] 컨테이너 메모리 실측(512MB/1GB 제한, MaxHeapSize 확인) — 문제 없어 Dockerfile 무변경
- [x] `docs/deploy-guide.md`에 Wait for CI 안내 + 롤백 런북 추가
- [x] `docs/dev/cd/deploy/design.md` 갱신
- [ ] 커밋·push 후 실제 배포 중 무중단 실측(프로덕션 URL 폴링)
- [ ] 사용자가 Railway "Wait for CI" 토글 활성화 + 실제 게이팅 동작 확인

## 평가(통과) 기준

- `./gradlew build` 기존 141케이스 회귀 없음
- `/actuator/health`가 인증 없이 200을 반환하고, 다른 actuator 엔드포인트는 여전히 막혀있음을 실측
- 실제 배포 중 프로덕션 URL 폴링으로 다운타임(비-200) 없음을 실측(또는 실제로 발견되면 정직하게 기록)
