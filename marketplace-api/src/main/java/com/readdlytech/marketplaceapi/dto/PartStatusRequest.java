package com.readdlytech.marketplaceapi.dto;

import com.readdlytech.marketplaceapi.entity.PartStatus;
import jakarta.validation.constraints.NotNull;

public record PartStatusRequest(@NotNull(message = "Status is required") PartStatus status) {}
