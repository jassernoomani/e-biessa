package com.readdlytech.marketplaceapi.controller;

import com.readdlytech.marketplaceapi.dto.AiSearchFilter;
import com.readdlytech.marketplaceapi.dto.SemanticSearchResponse;
import com.readdlytech.marketplaceapi.entity.User;
import com.readdlytech.marketplaceapi.security.CurrentUserService;
import com.readdlytech.marketplaceapi.service.AiMarketplaceService;
import com.readdlytech.marketplaceapi.service.AiSearchRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Validated
public class AiSearchController {
    private final AiMarketplaceService aiMarketplaceService;
    private final CurrentUserService currentUserService;
    private final AiSearchRateLimiter rateLimiter;

    @GetMapping("/search")
    public SemanticSearchResponse search(
            @RequestParam @Size(min = 2, max = 240) String query,
            @RequestParam(defaultValue = "12") @Min(1) @Max(50) int limit,
            @RequestParam(defaultValue = "0.50") double minimumScore,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String condition,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String fuelType,
            @RequestParam(required = false) String partPosition,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            Authentication authentication,
            HttpServletRequest request) {
        if (minPrice != null && maxPrice != null && maxPrice.compareTo(minPrice) < 0) {
            throw new IllegalArgumentException("Maximum price must be greater than or equal to minimum price");
        }
        Long userId = currentUserService.optional(authentication).map(User::getId).orElse(null);
        rateLimiter.check(userId == null ? "ip:" + request.getRemoteAddr() : "user:" + userId);
        if (minimumScore < 0 || minimumScore > 1) throw new IllegalArgumentException("Minimum score must be between 0 and 1");
        return aiMarketplaceService.search(query.trim(), limit, minimumScore,
                new AiSearchFilter(brand, model, year, condition, category, location, fuelType, partPosition,
                        minPrice, maxPrice), userId);
    }
}
