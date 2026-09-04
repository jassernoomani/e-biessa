package com.readdlytech.marketplaceapi.dto;

import com.readdlytech.marketplaceapi.entity.ReportStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReportResolutionRequest(
        @NotNull(message = "Resolution is required") ReportStatus status,
        @Size(max = 1000, message = "Administrator note must be 1000 characters or fewer") String adminNote
) {}
