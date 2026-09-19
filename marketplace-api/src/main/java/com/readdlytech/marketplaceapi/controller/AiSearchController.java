package com.readdlytech.marketplaceapi.controller;

import com.readdlytech.marketplaceapi.dto.AiSearchFilter;
import com.readdlytech.marketplaceapi.dto.SemanticSearchResponse;
import com.readdlytech.marketplaceapi.dto.MarketplaceAssistantResponse;
import com.readdlytech.marketplaceapi.dto.AiVisionResponse;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Validated
public class AiSearchController {
    private final AiMarketplaceService aiMarketplaceService;
    private final CurrentUserService currentUserService;
    private final AiSearchRateLimiter rateLimiter;
    private final com.readdlytech.marketplaceapi.service.BuyerActivityService buyerActivity;

    @org.springframework.beans.factory.annotation.Value("${app.storage.max-file-size-bytes:5242880}")
    private long maxImageBytes;

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
        if (!Double.isFinite(minimumScore) || minimumScore < 0 || minimumScore > 1) throw new IllegalArgumentException("Minimum score must be between 0 and 1");
        SemanticSearchResponse response = aiMarketplaceService.search(query.trim(), limit, minimumScore,
                new AiSearchFilter(brand, model, year, condition, category, location, fuelType, partPosition,
                        minPrice, maxPrice), userId);
        response.results().stream().map(result -> result.part().getCategoryId()).filter(java.util.Objects::nonNull)
                .distinct().limit(3).forEach(id -> buyerActivity.record(userId,
                        com.readdlytech.marketplaceapi.entity.BuyerInteraction.Kind.SEARCH, id, id, null));
        return response;
    }

    @GetMapping("/assistant")
    public MarketplaceAssistantResponse assistant(
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
        if (minPrice != null && maxPrice != null && maxPrice.compareTo(minPrice) < 0)
            throw new IllegalArgumentException("Maximum price must be greater than or equal to minimum price");
        if (!Double.isFinite(minimumScore) || minimumScore < 0 || minimumScore > 1)
            throw new IllegalArgumentException("Minimum score must be between 0 and 1");
        User actor = currentUserService.require(authentication);
        rateLimiter.check("user:" + actor.getId());
        MarketplaceAssistantResponse response = aiMarketplaceService.assist(query.trim(), limit, minimumScore,
                new AiSearchFilter(brand, model, year, condition, category, location, fuelType, partPosition,
                        minPrice, maxPrice), actor);
        response.results().stream().map(result -> result.part().getCategoryId()).filter(java.util.Objects::nonNull)
                .distinct().limit(3).forEach(id -> buyerActivity.record(actor.getId(),
                        com.readdlytech.marketplaceapi.entity.BuyerInteraction.Kind.SEARCH, id, id, null));
        return response;
    }

    @PostMapping(value = "/identify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AiVisionResponse identifyPartImage(
            @org.springframework.web.bind.annotation.RequestPart("file") MultipartFile file,
            Authentication authentication) {
        User actor = currentUserService.require(authentication);
        rateLimiter.check("vision-user:" + actor.getId());
        if (file.isEmpty()) throw new IllegalArgumentException("Choose a non-empty image.");
        if (file.getSize() > maxImageBytes) throw new IllegalArgumentException("The image exceeds the 5 MB limit.");
        String contentType = file.getContentType();
        if (contentType == null || !java.util.Set.of("image/jpeg", "image/png", "image/webp").contains(contentType))
            throw new IllegalArgumentException("Only JPEG, PNG, and WebP images are supported.");
        try {
            return aiMarketplaceService.identifyPartImage(file.getBytes(), MediaType.parseMediaType(contentType));
        } catch (java.io.IOException exception) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                                       "The uploaded image could not be read.");
        }
    }
}
