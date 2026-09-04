package com.readdlytech.marketplaceapi.dto;

import java.math.BigDecimal;

public record AiSearchFilter(
        String brand,
        String model,
        Integer year,
        String condition,
        String category,
        String location,
        String fuelType,
        String partPosition,
        BigDecimal minPrice,
        BigDecimal maxPrice
) {}
