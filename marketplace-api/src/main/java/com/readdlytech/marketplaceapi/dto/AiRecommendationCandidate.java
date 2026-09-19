package com.readdlytech.marketplaceapi.dto;

import java.util.List;

public record AiRecommendationCandidate(Long listingId, double score, List<String> reasons) {}
