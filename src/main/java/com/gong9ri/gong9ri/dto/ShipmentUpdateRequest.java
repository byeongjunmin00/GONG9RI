package com.gong9ri.gong9ri.dto;

import com.gong9ri.gong9ri.entity.ShipmentStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ShipmentUpdateRequest(
        @NotNull ShipmentStatus shipmentStatus,
        // 두 값 모두 payment 테이블에서 VARCHAR(50)이다. 길이 제한이 없으면 검증을 통과해 **커밋 시점에**
        // MySQL data truncation으로 터지는데, 사용자 입력 문제인데도 500이 나간다(업로드 용량 초과가
        // 500을 내던 것과 같은 부류). 컬럼 길이와 같은 값으로 막아 400으로 되돌린다.
        @Size(max = 50) String trackingCarrier,
        @Size(max = 50) String trackingNumber
) {
}
