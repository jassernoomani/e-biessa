package com.readdlytech.marketplaceapi.controller;

import com.readdlytech.marketplaceapi.dto.PartResponse;
import com.readdlytech.marketplaceapi.dto.SellerContactResponse;
import com.readdlytech.marketplaceapi.dto.SellerProfileResponse;
import com.readdlytech.marketplaceapi.dto.SellerReviewRequest;
import com.readdlytech.marketplaceapi.dto.SellerReviewSummaryResponse;
import com.readdlytech.marketplaceapi.entity.User;
import com.readdlytech.marketplaceapi.security.CurrentUserService;
import com.readdlytech.marketplaceapi.service.SellerService;
import com.readdlytech.marketplaceapi.service.SellerReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;

import java.util.List;

@RestController
@RequestMapping("/api/sellers")
@RequiredArgsConstructor
public class SellerController {
    private final SellerService sellerService;
    private final SellerReviewService sellerReviewService;
    private final CurrentUserService currentUserService;
    private final com.readdlytech.marketplaceapi.service.BuyerActivityService buyerActivity;

    @GetMapping("/{sellerId}")
    public ResponseEntity<SellerProfileResponse> profile(@PathVariable Long sellerId) {
        return ResponseEntity.ok(sellerService.getProfile(sellerId));
    }

    @GetMapping("/{sellerId}/listings")
    public ResponseEntity<List<PartResponse>> listings(@PathVariable Long sellerId,
                                                        Authentication authentication) {
        Long viewerId = currentUserService.optional(authentication).map(User::getId).orElse(null);
        return ResponseEntity.ok(sellerService.getActiveListings(sellerId, viewerId));
    }

    @GetMapping("/{sellerId}/contact")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<SellerContactResponse> contact(@PathVariable Long sellerId, Authentication authentication) {
        SellerContactResponse response = sellerService.getContact(sellerId);
        buyerActivity.record(currentUserService.require(authentication).getId(),
                com.readdlytech.marketplaceapi.entity.BuyerInteraction.Kind.CONTACT, sellerId, null, sellerId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{sellerId}/reviews")
    public ResponseEntity<SellerReviewSummaryResponse> reviews(@PathVariable Long sellerId,
                                                                Authentication authentication) {
        return ResponseEntity.ok(sellerReviewService.summary(sellerId,
                currentUserService.optional(authentication).orElse(null)));
    }

    @PutMapping("/{sellerId}/reviews/me")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<SellerReviewSummaryResponse> saveReview(
            @PathVariable Long sellerId,
            @Valid @RequestBody SellerReviewRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(sellerReviewService.save(sellerId, request,
                currentUserService.require(authentication)));
    }
}
