package com.readdlytech.marketplaceapi.dto;

import java.util.List;

public record AiAssistantResult(Long listingId, double score, List<String> matchReasons, String source) {}
