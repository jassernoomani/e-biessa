package com.readdlytech.marketplaceapi.dto;

import java.util.List;

public record AiVisionResponse(List<AiVisionCandidateResponse> candidates, String model, String caution) {}
