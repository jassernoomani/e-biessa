package com.readdlytech.marketplaceapi.repository;

import com.readdlytech.marketplaceapi.entity.ListingReport;
import com.readdlytech.marketplaceapi.entity.ReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ListingReportRepository extends JpaRepository<ListingReport, Long> {
    boolean existsByReporterIdAndPartId(Long reporterId, Long partId);
    List<ListingReport> findAllByOrderByCreatedAtDesc();
    List<ListingReport> findByStatusOrderByCreatedAtDesc(ReportStatus status);
}
