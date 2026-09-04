package com.readdlytech.marketplaceapi.dto;

import com.readdlytech.marketplaceapi.entity.SellerReportReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SellerReportRequest(
        @NotNull(message = "Select a report reason") SellerReportReason reason,
        @NotBlank(message = "Explain what happened")
        @Size(min = 10, max = 1000, message = "Report details must be between 10 and 1000 characters") String details
) {}
