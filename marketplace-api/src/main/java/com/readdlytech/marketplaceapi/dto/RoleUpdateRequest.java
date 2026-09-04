package com.readdlytech.marketplaceapi.dto;

import com.readdlytech.marketplaceapi.entity.UserRole;
import jakarta.validation.constraints.NotNull;

public record RoleUpdateRequest(@NotNull(message = "Role is required") UserRole role) {}
