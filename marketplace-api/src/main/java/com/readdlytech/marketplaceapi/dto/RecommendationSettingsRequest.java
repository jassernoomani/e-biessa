package com.readdlytech.marketplaceapi.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public record RecommendationSettingsRequest(@NotNull Boolean personalizationEnabled,
        @NotNull @Size(max = 5) List<@NotNull @Positive Long> vehicleIds) {}
