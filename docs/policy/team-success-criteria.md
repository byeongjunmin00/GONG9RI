# 공구팀 성사 판정 기준

## 규칙

`group_buy_team.current_count`가 `max_participants`에 도달하는 즉시(참가 API 처리 중) `status`를 `RECRUITING → SUCCESS`로 전환한다. 배치나 지연 확인 없이 실시간으로 처리한다.

## 근거 / 배경

정원(목표 인원)이 가격을 결정하고(`payment/crud`), 정원에 도달하면 더 이상 참가할 자리가 없으므로, 참가자에게 그 자리에서 바로 팀 성사를 알려줘야 자연스럽다. 참가(`join`) 처리 자체가 `current_count` 갱신 트랜잭션 안에서 일어나므로, 같은 트랜잭션에서 정원 도달 여부를 확인해 상태를 같이 바꾼다.

## 적용 대상

- `team/join` (`POST /api/teams/{teamId}/join`)
