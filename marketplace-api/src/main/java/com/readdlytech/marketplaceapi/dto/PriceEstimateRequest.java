package com.readdlytech.marketplaceapi.dto;

import jakarta.validation.constraints.Size;

import java.util.LinkedHashSet;
import java.util.Set;

public record PriceEstimateRequest(
        Long excludeListingId,
        @Size(max = 255) String title,
        Long categoryId,
        @Size(max = 20) Set<Long> vehicleIds,
        @Size(max = 50) String condition,
        @Size(max = 120) String partBrand,
        @Size(max = 100) String oemNumber,
        @Size(max = 30) String partPosition
) {
    public PriceEstimateRequest {
        vehicleIds = vehicleIds == null ? new LinkedHashSet<>() : new LinkedHashSet<>(vehicleIds);
    }
}
