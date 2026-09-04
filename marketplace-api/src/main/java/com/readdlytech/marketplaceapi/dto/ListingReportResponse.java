package com.readdlytech.marketplaceapi.dto;

import com.readdlytech.marketplaceapi.entity.ListingReport;

import java.time.LocalDateTime;

public record ListingReportResponse(
        Long id, Long partId, String partTitle, String partStatus,
        Long sellerId, String sellerName, Long reporterId, String reporterName,
        String reason, String details, String status, String adminNote,
        LocalDateTime createdAt, LocalDateTime resolvedAt
) {
    public static ListingReportResponse from(ListingReport report) {
        return new ListingReportResponse(
                report.getId(), report.getPart().getId(), report.getPart().getTitle(),
                report.getPart().getStatus().name(), report.getPart().getSeller().getId(),
                report.getPart().getSeller().getFullName(), report.getReporter().getId(),
                report.getReporter().getFullName(), report.getReason().name(), report.getDetails(),
                report.getStatus().name(), report.getAdminNote(), report.getCreatedAt(), report.getResolvedAt());
    }
}
