package com.readdlytech.marketplaceapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserProfileRequest(
        @NotBlank(message = "Full name is required") @Size(max = 100) String fullName,
        @Size(max = 20, message = "Phone must be 20 characters or fewer") String phone,
        @Size(max = 120, message = "Location must be 120 characters or fewer") String location,
        @Size(max = 160, message = "Business name must be 160 characters or fewer") String businessName,
        @Size(max = 255, message = "Website must be 255 characters or fewer") String website,
        @Size(max = 1000, message = "Biography must be 1000 characters or fewer") String bio
) {}
