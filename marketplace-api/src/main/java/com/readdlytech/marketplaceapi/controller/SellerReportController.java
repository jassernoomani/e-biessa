package com.readdlytech.marketplaceapi.controller;

import com.readdlytech.marketplaceapi.dto.SellerReportRequest;
import com.readdlytech.marketplaceapi.dto.SellerReportResponse;
import com.readdlytech.marketplaceapi.security.CurrentUserService;
import com.readdlytech.marketplaceapi.service.SellerReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sellers/{sellerId}/reports")
@RequiredArgsConstructor
public class SellerReportController {
    private final SellerReportService reportService;
    private final CurrentUserService currentUserService;

    @PostMapping
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<SellerReportResponse> create(
            @PathVariable Long sellerId,
            @Valid @RequestBody SellerReportRequest request,
            Authentication authentication) {
        return ResponseEntity.status(201).body(reportService.create(sellerId, request,
                currentUserService.require(authentication)));
    }
}
