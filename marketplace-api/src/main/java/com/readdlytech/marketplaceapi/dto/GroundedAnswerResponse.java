package com.readdlytech.marketplaceapi.dto;

import java.util.List;

public record GroundedAnswerResponse(
        String summary,
        List<Long> recommendedListingIds,
        List<String> cautions,
        String generatedBy
) {
    public static GroundedAnswerResponse empty() {
        return new GroundedAnswerResponse("", List.of(), List.of(), "deterministic");
    }
}
