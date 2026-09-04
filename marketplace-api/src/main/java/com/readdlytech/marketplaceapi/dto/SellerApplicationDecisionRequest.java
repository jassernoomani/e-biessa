package com.readdlytech.marketplaceapi.dto;

import jakarta.validation.constraints.Size;

public record SellerApplicationDecisionRequest(
        boolean approved,
        @Size(max = 500, message = "Review note must be 500 characters or fewer") String reason
) {}
