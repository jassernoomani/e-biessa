package com.readdlytech.marketplaceapi.dto;

import java.time.LocalDateTime;

public record SellerProfileResponse(
        Long id,
        String fullName,
        String businessName,
        String location,
        String website,
        String bio,
        LocalDateTime memberSince,
        long activeListingCount,
        double averageRating,
        long reviewCount
) {}
