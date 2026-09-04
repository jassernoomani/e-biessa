package com.readdlytech.marketplaceapi.controller;

import com.readdlytech.marketplaceapi.dto.PartResponse;
import com.readdlytech.marketplaceapi.dto.PartStatusRequest;
import com.readdlytech.marketplaceapi.dto.RoleUpdateRequest;
import com.readdlytech.marketplaceapi.dto.UserResponse;
import com.readdlytech.marketplaceapi.dto.UserProfileRequest;
import com.readdlytech.marketplaceapi.dto.ListingReportResponse;
import com.readdlytech.marketplaceapi.dto.ReportResolutionRequest;
import com.readdlytech.marketplaceapi.dto.SuspensionRequest;
import com.readdlytech.marketplaceapi.dto.AdminAuditLogResponse;
import com.readdlytech.marketplaceapi.dto.SellerApplicationDecisionRequest;
import com.readdlytech.marketplaceapi.dto.SellerReportResponse;
import com.readdlytech.marketplaceapi.entity.ReportStatus;
import com.readdlytech.marketplaceapi.entity.User;
import com.readdlytech.marketplaceapi.security.CurrentUserService;
import com.readdlytech.marketplaceapi.service.AdminService;
import com.readdlytech.marketplaceapi.service.ListingReportService;
import com.readdlytech.marketplaceapi.service.AdminAuditService;
import com.readdlytech.marketplaceapi.service.SellerReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    private final AdminService adminService;
    private final CurrentUserService currentUserService;
    private final ListingReportService reportService;
    private final AdminAuditService auditService;
    private final SellerReportService sellerReportService;

    @GetMapping("/users")
    public ResponseEntity<List<UserResponse>> users() {
        return ResponseEntity.ok(adminService.users());
    }

    @GetMapping("/seller-applications")
    public ResponseEntity<List<UserResponse>> sellerApplications() {
        return ResponseEntity.ok(adminService.sellerApplications());
    }

    @PatchMapping("/seller-applications/{userId}")
    public ResponseEntity<UserResponse> decideSellerApplication(
            @PathVariable Long userId,
            @Valid @RequestBody SellerApplicationDecisionRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(adminService.decideSellerApplication(userId, request.approved(), request.reason(),
                currentUserService.require(authentication)));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<UserResponse> user(@PathVariable Long userId) {
        return ResponseEntity.ok(adminService.user(userId));
    }

    @PutMapping("/users/{userId}/profile")
    public ResponseEntity<UserResponse> profile(@PathVariable Long userId,
                                                 @Valid @RequestBody UserProfileRequest request,
                                                 Authentication authentication) {
        return ResponseEntity.ok(adminService.updateProfile(userId, request, currentUserService.require(authentication)));
    }

    @PatchMapping("/users/{userId}/suspension")
    public ResponseEntity<UserResponse> suspension(@PathVariable Long userId, @Valid @RequestBody SuspensionRequest request,
                                                    Authentication authentication) {
        return ResponseEntity.ok(adminService.updateSuspension(userId, request.suspended(), request.reason(),
                currentUserService.require(authentication)));
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<List<AdminAuditLogResponse>> auditLogs() { return ResponseEntity.ok(auditService.latest()); }

    @PatchMapping("/users/{userId}/role")
    public ResponseEntity<UserResponse> role(@PathVariable Long userId,
                                             @Valid @RequestBody RoleUpdateRequest request,
                                             Authentication authentication) {
        User admin = currentUserService.require(authentication);
        return ResponseEntity.ok(adminService.updateRole(userId, request.role(), admin));
    }

    @GetMapping("/parts")
    public ResponseEntity<Page<PartResponse>> parts(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(adminService.parts(pageable));
    }

    @PatchMapping("/parts/{partId}/status")
    public ResponseEntity<PartResponse> status(@PathVariable Long partId,
                                               @Valid @RequestBody PartStatusRequest request,
                                               Authentication authentication) {
        return ResponseEntity.ok(adminService.moderate(partId, request.status(), currentUserService.require(authentication)));
    }

    @DeleteMapping("/parts/{partId}")
    public ResponseEntity<Void> delete(@PathVariable Long partId, Authentication authentication) {
        adminService.deletePart(partId, currentUserService.require(authentication));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/reports")
    public ResponseEntity<List<ListingReportResponse>> reports(
            @RequestParam(required = false) ReportStatus status) {
        return ResponseEntity.ok(reportService.list(status));
    }

    @GetMapping("/seller-reports")
    public ResponseEntity<List<SellerReportResponse>> sellerReports(
            @RequestParam(required = false) ReportStatus status) {
        return ResponseEntity.ok(sellerReportService.list(status));
    }

    @PatchMapping("/seller-reports/{reportId}")
    public ResponseEntity<SellerReportResponse> resolveSellerReport(
            @PathVariable Long reportId,
            @Valid @RequestBody ReportResolutionRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(sellerReportService.resolve(reportId, request,
                currentUserService.require(authentication)));
    }

    @PatchMapping("/reports/{reportId}")
    public ResponseEntity<ListingReportResponse> resolveReport(
            @PathVariable Long reportId,
            @Valid @RequestBody ReportResolutionRequest request,
            Authentication authentication) {
        User admin = currentUserService.require(authentication);
        ListingReportResponse response = reportService.resolve(reportId, request, admin);
        auditService.record(admin, "LISTING_REPORT_RESOLVED", "REPORT", reportId, response.partTitle(),
                "Report marked " + request.status());
        return ResponseEntity.ok(response);
    }
}
