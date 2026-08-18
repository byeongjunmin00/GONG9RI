# inquiry API

> 응답 형식(성공/실패 공통): 공통 규칙 — [api/README.md](README.md). 아래 응답 예시는 성공 시 `data` 안에 들어갈 내용만 표시.
> 실패: `{ "success": false, "code": "...", "message": "..." }`

> **리뷰와의 차이**: 문의는 구매 이력을 요구하지 않는다(구매 전 질문이 핵심 용도). 목록은 비로그인
> 포함 누구나 조회 가능하고, 작성자 실명(닉네임)은 리뷰와 동일하게 마스킹 없이 노출된다.
> 비밀글(작성자/판매자만 볼 수 있는 문의) 기능은 이번 스코프에 없다 — 전체 공개.

---

## GET /api/products/{productId}/inquiries — 상품 문의 목록 조회

> 비로그인도 조회 가능(상품 상세 페이지의 공개 정보). 페이지네이션 없음(리뷰와 동일한 단순 목록 —
> 문의가 많이 쌓이는 상품은 응답이 커질 수 있다는 점은 계획 문서의 리스크로 기록).

- 경로 변수: `productId` (Long)

- 응답: `200 OK`
  ```json
  {
    "count": 2,
    "inquiries": [
      {
        "inquiryId": 5,
        "productId": 1,
        "memberId": 7,
        "memberName": "홍길동",
        "content": "배송은 얼마나 걸리나요?",
        "answered": true,
        "answerContent": "평균 2~3일 소요됩니다.",
        "answeredAt": "2026-08-15T09:00:00",
        "createdAt": "2026-08-14T10:00:00",
        "updatedAt": "2026-08-14T10:00:00"
      }
    ]
  }
  ```
  > 정렬은 최신 작성순(`createdAt DESC`). 문의가 하나도 없으면 `count`는 `0`, `inquiries`는 빈 배열.
  > `answered`는 `answerContent`의 null 여부로 계산되는 파생 값.

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `PRODUCT_NOT_FOUND` | 404 | 존재하지 않는 상품 |

---

## POST /api/products/{productId}/inquiries — 문의 작성

로그인한 회원이면 role·구매 이력과 무관하게 작성할 수 있다.

- 경로 변수: `productId` (Long)
- 요청 body:
  | 필드 | 타입 | 필수 | 설명 |
  |------|------|------|------|
  | content | string | Y | 최대 1000자 |

- 응답: `201 Created` — 위 문의 객체 형식(`answered: false`, `answerContent: null`, `answeredAt: null`)

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `PRODUCT_NOT_FOUND` | 404 | 존재하지 않는 상품 |
  | `UNAUTHORIZED` | 401 | 미인증 |
  | `VALIDATION_FAILED` | 400 | `content` 누락/공백/길이 초과 |

---

## PUT /api/inquiries/{inquiryId} — 문의 내용 수정

작성자 본인만, **아직 답변이 등록되지 않은 문의만** 수정할 수 있다.

- 경로 변수: `inquiryId` (Long)
- 요청 body: `POST`와 동일(content)
- 응답: `200 OK` — 수정된 문의 객체

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `INQUIRY_NOT_FOUND` | 404 | 존재하지 않는 문의 |
  | `FORBIDDEN` | 403 | 본인이 작성한 문의가 아님 |
  | `UNAUTHORIZED` | 401 | 미인증 |
  | `INQUIRY_ALREADY_ANSWERED` | 409 | 이미 답변이 등록된 문의라 수정 불가 |
  | `VALIDATION_FAILED` | 400 | `content` 누락/공백/길이 초과 |

---

## DELETE /api/inquiries/{inquiryId} — 문의 삭제

작성자 본인만, **아직 답변이 등록되지 않은 문의만** 삭제할 수 있다(하드 삭제).

- 경로 변수: `inquiryId` (Long)
- 응답: `204 No Content`

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `INQUIRY_NOT_FOUND` | 404 | 존재하지 않는 문의 |
  | `FORBIDDEN` | 403 | 본인이 작성한 문의가 아님 |
  | `UNAUTHORIZED` | 401 | 미인증 |
  | `INQUIRY_ALREADY_ANSWERED` | 409 | 이미 답변이 등록된 문의라 삭제 불가 |

---

## POST /api/inquiries/{inquiryId}/answer — 판매자 답변 등록

그 문의가 달린 상품을 등록한 판매자 본인만, **아직 답변이 없는 문의에만** 등록할 수 있다.

- 경로 변수: `inquiryId` (Long)
- 요청 body:
  | 필드 | 타입 | 필수 | 설명 |
  |------|------|------|------|
  | content | string | Y | 최대 1000자 |

- 응답: `201 Created` — 문의 객체(`answered: true`로 갱신)

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `INQUIRY_NOT_FOUND` | 404 | 존재하지 않는 문의 |
  | `FORBIDDEN` | 403 | 그 상품의 판매자 본인이 아님(구매자 계정 포함) |
  | `UNAUTHORIZED` | 401 | 미인증 |
  | `INQUIRY_ALREADY_ANSWERED` | 409 | 이미 답변이 등록됨 |
  | `VALIDATION_FAILED` | 400 | `content` 누락/공백/길이 초과 |

---

## PUT /api/inquiries/{inquiryId}/answer — 판매자 답변 수정

- 경로 변수: `inquiryId` (Long)
- 요청 body: 위 등록과 동일(content)
- 응답: `200 OK` — 수정된 문의 객체

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `INQUIRY_NOT_FOUND` | 404 | 존재하지 않는 문의 |
  | `ANSWER_NOT_FOUND` | 404 | 아직 등록된 답변이 없음 |
  | `FORBIDDEN` | 403 | 그 상품의 판매자 본인이 아님 |
  | `UNAUTHORIZED` | 401 | 미인증 |
  | `VALIDATION_FAILED` | 400 | `content` 누락/공백/길이 초과 |

---

## DELETE /api/inquiries/{inquiryId}/answer — 판매자 답변 삭제

답변만 삭제되고 문의(질문)는 남아 다시 "미답변" 상태가 된다.

- 경로 변수: `inquiryId` (Long)
- 응답: `204 No Content`

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `INQUIRY_NOT_FOUND` | 404 | 존재하지 않는 문의 |
  | `ANSWER_NOT_FOUND` | 404 | 아직 등록된 답변이 없음 |
  | `FORBIDDEN` | 403 | 그 상품의 판매자 본인이 아님 |
  | `UNAUTHORIZED` | 401 | 미인증 |

---

## 신규 에러 코드 (`ErrorCode`에 추가 필요)

| 코드 | HTTP | 메시지 |
|------|------|--------|
| `INQUIRY_NOT_FOUND` | 404 | 존재하지 않는 문의입니다. |
| `INQUIRY_ALREADY_ANSWERED` | 409 | 이미 답변이 등록된 문의는 수정/삭제할 수 없습니다. |
| `ANSWER_NOT_FOUND` | 404 | 등록된 답변이 없습니다. |

> `PRODUCT_NOT_FOUND`/`FORBIDDEN`/`UNAUTHORIZED`/`VALIDATION_FAILED`는 기존 코드를 재사용한다.

## 알림 연동 — 이번 스코프 제외 (향후 확장 후보)

문의 작성/답변 시점에 기존 `notification` 인프라(`docs/db/notification.md`)로 알림을 남기는 것도
고려했지만, 그 테이블은 현재 `related_team_id`만 갖고 있어 문의 알림을 걸려면 공유 테이블 스키마까지
바꿔야 한다(`related_inquiry_id` 등 추가). 이번 라운드는 상품 상세 페이지의 문의 CRUD 자체에
집중하고, 알림 연동은 별도 계획으로 분리한다(`docs/dev/ongoing/` 후속 작업 후보).
