# 001-env-config — CI/CD 대비 DB 설정 환경변수화 (로그)

## Attempt 1 — 2026-07-28  ✅ PASS
- 시도: `src/main/resources/application.yaml`의 datasource(`url`/`username`/`password`)를 하드코딩 값에서 `${VAR:기본값}` 형태로 변경. 로컬 개발 시 기존과 동일한 기본값(root/1234/localhost)이 그대로 적용되고, 배포 환경별로는 환경변수 주입만으로 파일 수정 없이 값을 교체할 수 있게 함.
- 결과: `./gradlew compileJava` BUILD SUCCESSFUL. 런타임(Spring 컨텍스트 로딩 후 실제 DB 연결)은 로컬 MySQL 미가동으로 별도 확인하지 않음.
- 증거: 해당 없음 (API 샘플이 아닌 설정 파일 변경).
