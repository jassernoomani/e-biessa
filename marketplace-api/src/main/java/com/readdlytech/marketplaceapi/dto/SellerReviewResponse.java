package com.readdlytech.marketplaceapi.dto;

import com.readdlytech.marketplaceapi.entity.SellerReview;

import java.time.LocalDateTime;

public record SellerReviewResponse(
        Long id,
        Long buyerId,
        String buyerName,
        int rating,
        String comment,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static SellerReviewResponse from(SellerReview review) {
        return new SellerReviewResponse(review.getId(), review.getBuyer().getId(),
                review.getBuyer().getFullName(), review.getRating(), review.getComment(),
                review.getCreatedAt(), review.getUpdatedAt());
    }
}
