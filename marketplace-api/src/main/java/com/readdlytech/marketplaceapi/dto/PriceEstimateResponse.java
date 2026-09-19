package com.readdlytech.marketplaceapi.dto;

import java.math.BigDecimal;
import java.util.List;

public record PriceEstimateResponse(
        BigDecimal suggestedPrice,
        BigDecimal lowerRange,
        BigDecimal upperRange,
        int comparableCount,
        String confidence,
        List<Long> comparableListingIds,
        List<String> reasons,
        String caution
) {}
