package com.readdlytech.marketplaceapi.service;

import com.readdlytech.marketplaceapi.dto.ListingReportRequest;
import com.readdlytech.marketplaceapi.dto.ListingReportResponse;
import com.readdlytech.marketplaceapi.dto.ReportResolutionRequest;
import com.readdlytech.marketplaceapi.entity.*;
import com.readdlytech.marketplaceapi.exception.ConflictException;
import com.readdlytech.marketplaceapi.exception.ResourceNotFoundException;
import com.readdlytech.marketplaceapi.repository.ListingReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ListingReportService {
    private final ListingReportRepository reportRepository;
    private final PartService partService;

    @Transactional
    public ListingReportResponse create(Long partId, ListingReportRequest request, User reporter) {
        Part part = partService.findPart(partId);
        if (!partService.isPubliclyVisible(part)) {
            throw new ResourceNotFoundException("Part not found");
        }
        if (part.getSeller().getId().equals(reporter.getId())) {
            throw new IllegalArgumentException("You cannot report your own listing");
        }
        if (reportRepository.existsByReporterIdAndPartId(reporter.getId(), partId)) {
            throw new ConflictException("You have already reported this listing");
        }
        ListingReport report = new ListingReport();
        report.setPart(part);
        report.setReporter(reporter);
        report.setReason(request.reason());
        report.setDetails(clean(request.details()));
        report.setStatus(ReportStatus.PENDING);
        return ListingReportResponse.from(reportRepository.save(report));
    }

    @Transactional(readOnly = true)
    public List<ListingReportResponse> list(ReportStatus status) {
        List<ListingReport> reports = status == null
                ? reportRepository.findAllByOrderByCreatedAtDesc()
                : reportRepository.findByStatusOrderByCreatedAtDesc(status);
        return reports.stream().map(ListingReportResponse::from).toList();
    }

    @Transactional
    public ListingReportResponse resolve(Long reportId, ReportResolutionRequest request, User admin) {
        if (request.status() == ReportStatus.PENDING) {
            throw new IllegalArgumentException("Choose Dismissed or Actioned as the resolution");
        }
        ListingReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));
        if (report.getStatus() != ReportStatus.PENDING) {
            throw new ConflictException("This report has already been resolved");
        }
        report.setStatus(request.status());
        report.setAdminNote(clean(request.adminNote()));
        report.setResolvedBy(admin);
        report.setResolvedAt(LocalDateTime.now());
        if (request.status() == ReportStatus.ACTIONED) {
            report.getPart().setStatus(PartStatus.INACTIVE);
            report.getPart().setSoldAt(null);
            partService.removeFromAiIndexAfterCommit(report.getPart().getId());
        }
        return ListingReportResponse.from(reportRepository.save(report));
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
