package com.readdlytech.marketplaceapi.dto;
import java.util.List;

public record RecommendationSettingsResponse(boolean personalizationEnabled, List<VehicleResponse> vehicles) {}
