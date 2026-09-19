package com.readdlytech.marketplaceapi.dto;

import java.util.List;
import java.util.Map;

public record AiServiceAssistantResponse(String query, String intent, List<AiAssistantResult> results, long tookMs,
                                         Map<String, Object> extractedConstraints, List<String> toolsUsed,
                                         GroundedAnswerResponse answer) {}
