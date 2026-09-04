package com.readdlytech.marketplaceapi.dto;

import com.readdlytech.marketplaceapi.entity.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ListingReportRequest(
        @NotNull(message = "Select a report reason") ReportReason reason,
        @Size(max = 1000, message = "Report details must be 1000 characters or fewer") String details
) {}
