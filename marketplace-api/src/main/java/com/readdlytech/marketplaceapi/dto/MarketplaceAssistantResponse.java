package com.readdlytech.marketplaceapi.dto;

import java.util.List;
import java.util.Map;

public record MarketplaceAssistantResponse(String query, String intent, List<AssistantPartResult> results, long tookMs,
                                           Map<String, Object> extractedConstraints, List<String> toolsUsed,
                                           GroundedAnswerResponse answer) {}
