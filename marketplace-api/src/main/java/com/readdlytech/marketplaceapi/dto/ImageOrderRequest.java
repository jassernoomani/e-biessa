package com.readdlytech.marketplaceapi.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ImageOrderRequest(
        @NotEmpty(message = "Image order is required") List<Long> imageIds
) {}
