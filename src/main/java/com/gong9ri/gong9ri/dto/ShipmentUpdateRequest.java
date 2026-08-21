package com.gong9ri.gong9ri.dto;

import com.gong9ri.gong9ri.entity.ShipmentStatus;
import jakarta.validation.constraints.NotNull;

public record ShipmentUpdateRequest(
        @NotNull ShipmentStatus shipmentStatus,
        String trackingCarrier,
        String trackingNumber
) {
}
