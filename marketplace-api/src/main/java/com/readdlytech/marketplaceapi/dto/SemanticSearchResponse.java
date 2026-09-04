package com.readdlytech.marketplaceapi.dto;

import java.util.List;
import java.util.Map;

public record SemanticSearchResponse(String query, List<SemanticPartResult> results, long tookMs,
                                     Map<String, Object> extractedConstraints,
                                     GroundedAnswerResponse answer) {}
