package com.readdlytech.marketplaceapi.dto;

import java.util.List;
import java.util.Map;

public record AiServiceSearchResponse(String query, List<AiSearchHit> hits, long tookMs,
                                      Map<String, Object> extractedConstraints,
                                      GroundedAnswerResponse answer) {}
