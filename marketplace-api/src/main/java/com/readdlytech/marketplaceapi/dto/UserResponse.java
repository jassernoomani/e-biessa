package com.readdlytech.marketplaceapi.dto;

import com.readdlytech.marketplaceapi.entity.User;
import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String email,
        String fullName,
        String phone,
        String location,
        String businessName,
        String website,
        String bio,
        String role,
        String sellerApplicationStatus,
        LocalDateTime sellerApplicationSubmittedAt,
        LocalDateTime sellerApplicationReviewedAt,
        String sellerApplicationReviewNote,
        boolean suspended,
        String suspensionReason,
        LocalDateTime suspendedAt,
        LocalDateTime createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getPhone(),
                user.getLocation(), user.getBusinessName(), user.getWebsite(), user.getBio(),
                user.getRole().name(), applicationStatus(user), user.getSellerApplicationSubmittedAt(),
                user.getSellerApplicationReviewedAt(), user.getSellerApplicationReviewNote(),
                Boolean.TRUE.equals(user.getSuspended()), user.getSuspensionReason(),
                user.getSuspendedAt(), user.getCreatedAt());
    }

    private static String applicationStatus(User user) {
        if (user.getSellerApplicationStatus() != null) return user.getSellerApplicationStatus().name();
        return user.getRole() == com.readdlytech.marketplaceapi.entity.UserRole.BUYER ? "NONE" : "APPROVED";
    }
}
