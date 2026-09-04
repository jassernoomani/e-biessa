package com.readdlytech.marketplaceapi.dto;

import jakarta.validation.constraints.NotBlank;

public record OAuthExchangeRequest(@NotBlank(message = "Exchange code is required") String code) {}
