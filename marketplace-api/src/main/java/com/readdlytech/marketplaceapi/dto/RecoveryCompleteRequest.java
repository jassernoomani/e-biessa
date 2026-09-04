package com.readdlytech.marketplaceapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RecoveryCompleteRequest(
        @NotBlank(message = "Email or phone number is required") String identifier,
        @NotBlank(message = "Recovery code is required")
        @Pattern(regexp = "\\d{6}", message = "Enter the six-digit recovery code") String code,
        @NotBlank(message = "New password is required")
        @Size(min = 8, message = "Password must be at least 8 characters") String newPassword
) {}
