package com.readdlytech.marketplaceapi.controller;

import com.readdlytech.marketplaceapi.dto.ListingReportRequest;
import com.readdlytech.marketplaceapi.dto.ListingReportResponse;
import com.readdlytech.marketplaceapi.security.CurrentUserService;
import com.readdlytech.marketplaceapi.service.ListingReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/parts/{partId}/reports")
@RequiredArgsConstructor
public class ListingReportController {
    private final ListingReportService reportService;
    private final CurrentUserService currentUserService;

    @PostMapping
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<ListingReportResponse> create(@PathVariable Long partId,
                                                         @Valid @RequestBody ListingReportRequest request,
                                                         Authentication authentication) {
        return ResponseEntity.status(201).body(reportService.create(partId, request,
                currentUserService.require(authentication)));
    }
}
