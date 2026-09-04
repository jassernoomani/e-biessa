package com.readdlytech.marketplaceapi.dto;

import java.util.List;

public record SellerReviewSummaryResponse(
        double averageRating,
        long reviewCount,
        boolean eligible,
        SellerReviewResponse myReview,
        List<SellerReviewResponse> reviews
) {}
