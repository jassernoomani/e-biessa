package com.readdlytech.marketplaceapi.dto;

import java.util.List;

public record AiSearchHit(Long listingId, double score, double rawScore, List<String> matchReasons) {}
