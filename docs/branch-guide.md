# 브랜치 가이드 (branch-guide)

이 저장소의 git 브랜치 규칙이다.

> `basic-harness` 템플릿 원본은 `master`(보호) → `dev`(통합) → `feature/*` 3단 구조인데,
> 지금 GONG9RI는 2인 팀 + `main` 브랜치 하나만 있는 상태라 **`dev` 계층은 생략**하고 단순화했다.
> 팀 규모가 커지거나 배포 파이프라인이 생기면 그때 `dev` 계층을 다시 추가하는 걸 고려한다.

## 브랜치 모델

```
main   ← 통합 브랜치 (모든 작업의 출발점이자 병합 대상)
  ├── feature/team-join       ← 작업 1
  ├── feature/product-search  ← 작업 2
  └── ...   각 작업 → 완료 후 main으로 merge (PR 리뷰 후)
```

## 네이밍 규칙

- 모든 작업 브랜치: **`feature/{개념}-{기능}`**
  - 예: `feature/team-join`, `feature/product-search`
- 모두 **`main`에서 분기**한다.

## 작업 흐름

```
1. main에서 feature/{작업} 브랜치 생성 (git switch -c feature/{작업} main)
2. 그 브랜치에서 작업 (Plan → Generate → Evaluate)
3. 완료 → 커밋 → PR 생성 → 팀원 리뷰 → main으로 merge
```

## main 보호

- 지금은 로컬 pre-commit 훅으로 `main` 직접 커밋을 막고 있지 않다 — **PR을 거쳐서만 merge**하는 걸 팀 규칙으로 지킨다.
- 필요하면 GitHub 저장소 설정에서 `main` 브랜치 보호 규칙(직접 push 금지, PR 승인 필수)을 켜는 것을 고려한다.
