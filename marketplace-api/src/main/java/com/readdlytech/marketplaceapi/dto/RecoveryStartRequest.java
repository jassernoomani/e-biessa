package com.readdlytech.marketplaceapi.dto;

import jakarta.validation.constraints.NotBlank;

public record RecoveryStartRequest(@NotBlank(message = "Email or phone number is required") String identifier) {}
