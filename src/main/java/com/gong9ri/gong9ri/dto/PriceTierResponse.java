package com.gong9ri.gong9ri.dto;

import com.gong9ri.gong9ri.entity.PriceTier;

public record PriceTierResponse(Integer minCount, Integer price) {

    public static PriceTierResponse from(PriceTier priceTier) {
        return new PriceTierResponse(priceTier.getMinCount(), priceTier.getPrice());
    }
}
