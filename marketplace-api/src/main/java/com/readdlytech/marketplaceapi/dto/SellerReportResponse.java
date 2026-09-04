package com.readdlytech.marketplaceapi.dto;

import com.readdlytech.marketplaceapi.entity.SellerReport;

import java.time.LocalDateTime;

public record SellerReportResponse(
        Long id, Long sellerId, String sellerName, String businessName,
        Long reporterId, String reporterName, String reason, String details,
        String status, String adminNote, LocalDateTime createdAt, LocalDateTime resolvedAt
) {
    public static SellerReportResponse from(SellerReport report) {
        return new SellerReportResponse(report.getId(), report.getSeller().getId(),
                report.getSeller().getFullName(), report.getSeller().getBusinessName(),
                report.getReporter().getId(), report.getReporter().getFullName(),
                report.getReason().name(), report.getDetails(), report.getStatus().name(),
                report.getAdminNote(), report.getCreatedAt(), report.getResolvedAt());
    }
}
