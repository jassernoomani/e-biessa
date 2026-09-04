package com.readdlytech.marketplaceapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StartConversationRequest(@NotNull Long partId,
        @NotBlank @Size(max = 2000) String message) {}
