package com.readdlytech.marketplaceapi.repository;

import com.readdlytech.marketplaceapi.entity.ReportStatus;
import com.readdlytech.marketplaceapi.entity.SellerReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SellerReportRepository extends JpaRepository<SellerReport, Long> {
    boolean existsByReporterIdAndSellerIdAndStatus(Long reporterId, Long sellerId, ReportStatus status);
    List<SellerReport> findAllByOrderByCreatedAtDesc();
    List<SellerReport> findByStatusOrderByCreatedAtDesc(ReportStatus status);
}
