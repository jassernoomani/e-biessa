package com.readdlytech.marketplaceapi.dto;

import jakarta.validation.constraints.Size;

public record SuspensionRequest(boolean suspended,
        @Size(max = 500, message = "Suspension reason must be 500 characters or fewer") String reason) {}
