# prompt_template (AI 프롬프트 템플릿)

## 컬럼
| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, auto | 식별자 |
| category | VARCHAR(20) | NOT NULL, UNIQUE(`uk_category`) | 프롬프트 분기 카테고리(`FOOD`/`GENERAL`) |
| content | TEXT | NOT NULL | 프롬프트 본문. `{input}` 플레이스홀더에 판매자 입력 텍스트가 채워짐 |
| version | INT | NOT NULL | 프롬프트 수정 시 수동으로 1씩 증가 — 개선 이력 문서와 대조용 |
| created_at | DATETIME | NOT NULL | 생성 시각 |
| updated_at | DATETIME | NOT NULL | 마지막 수정 시각 |

## 인덱스
- `uk_category` (category, UNIQUE) — 카테고리당 템플릿 1개만 존재

## 관계
- 없음(독립 테이블)

## 사용하는 기능
- ai/product-suggestion — `PromptTemplateSeeder`가 기동 시 카테고리별 시드 없으면만 삽입(있으면 덮어쓰지 않음). 수정은 `UPDATE` 한 줄로 가능(재배포 불필요)

## 삭제 정책
- 하드 삭제 없음(카테고리별 항상 1행 존재해야 함)
