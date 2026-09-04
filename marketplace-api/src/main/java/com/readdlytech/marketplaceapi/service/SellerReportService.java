package com.readdlytech.marketplaceapi.service;

import com.readdlytech.marketplaceapi.dto.ReportResolutionRequest;
import com.readdlytech.marketplaceapi.dto.SellerReportRequest;
import com.readdlytech.marketplaceapi.dto.SellerReportResponse;
import com.readdlytech.marketplaceapi.entity.*;
import com.readdlytech.marketplaceapi.exception.ConflictException;
import com.readdlytech.marketplaceapi.exception.ResourceNotFoundException;
import com.readdlytech.marketplaceapi.repository.PartRepository;
import com.readdlytech.marketplaceapi.repository.SellerReportRepository;
import com.readdlytech.marketplaceapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SellerReportService {
    private final SellerReportRepository reportRepository;
    private final UserRepository userRepository;
    private final PartRepository partRepository;
    private final SellerService sellerService;
    private final AdminAuditService auditService;
    private final PartService partService;

    @Transactional
    public SellerReportResponse create(Long sellerId, SellerReportRequest request, User reporter) {
        sellerService.getProfile(sellerId);
        User seller = userRepository.findById(sellerId)
                .orElseThrow(() -> new ResourceNotFoundException("Seller not found"));
        if (seller.getId().equals(reporter.getId())) {
            throw new IllegalArgumentException("You cannot report your own seller profile");
        }
        if (reportRepository.existsByReporterIdAndSellerIdAndStatus(reporter.getId(), sellerId, ReportStatus.PENDING)) {
            throw new ConflictException("You already have a pending report for this seller");
        }
        SellerReport report = new SellerReport();
        report.setSeller(seller);
        report.setReporter(reporter);
        report.setReason(request.reason());
        report.setDetails(request.details().trim());
        return SellerReportResponse.from(reportRepository.save(report));
    }

    @Transactional(readOnly = true)
    public List<SellerReportResponse> list(ReportStatus status) {
        List<SellerReport> reports = status == null
                ? reportRepository.findAllByOrderByCreatedAtDesc()
                : reportRepository.findByStatusOrderByCreatedAtDesc(status);
        return reports.stream().map(SellerReportResponse::from).toList();
    }

    @Transactional
    public SellerReportResponse resolve(Long reportId, ReportResolutionRequest request, User admin) {
        if (request.status() == ReportStatus.PENDING) {
            throw new IllegalArgumentException("Choose Dismissed or Actioned as the resolution");
        }
        if (request.status() == ReportStatus.ACTIONED && blank(request.adminNote())) {
            throw new IllegalArgumentException("An administrator note is required before suspending a seller");
        }
        SellerReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Seller report not found"));
        if (report.getStatus() != ReportStatus.PENDING) {
            throw new ConflictException("This seller report has already been resolved");
        }
        report.setStatus(request.status());
        report.setAdminNote(clean(request.adminNote()));
        report.setResolvedBy(admin);
        report.setResolvedAt(LocalDateTime.now());
        if (request.status() == ReportStatus.ACTIONED) suspendReportedSeller(report, admin);
        SellerReport saved = reportRepository.save(report);
        auditService.record(admin, "SELLER_REPORT_RESOLVED", "USER", report.getSeller().getId(),
                report.getSeller().getEmail(), request.status() + (blank(request.adminNote()) ? "" : ": " + request.adminNote().trim()));
        return SellerReportResponse.from(saved);
    }

    private void suspendReportedSeller(SellerReport report, User admin) {
        User seller = report.getSeller();
        String reason = "Seller report #" + report.getId() + ": " + report.getAdminNote();
        seller.setSuspended(true);
        seller.setSuspensionReason(reason.length() > 500 ? reason.substring(0, 500) : reason);
        seller.setSuspendedAt(LocalDateTime.now());
        seller.setSuspendedById(admin.getId());
        partRepository.findBySellerAndStatusOrderByCreatedAtDesc(seller, PartStatus.ACTIVE)
                .forEach(part -> {
                    part.setStatus(PartStatus.INACTIVE);
                    partService.removeFromAiIndexAfterCommit(part.getId());
                });
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String clean(String value) { return blank(value) ? null : value.trim(); }
}
