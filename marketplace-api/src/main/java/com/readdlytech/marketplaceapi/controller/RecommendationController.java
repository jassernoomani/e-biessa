package com.readdlytech.marketplaceapi.controller;

import com.readdlytech.marketplaceapi.dto.*;
import com.readdlytech.marketplaceapi.security.CurrentUserService;
import com.readdlytech.marketplaceapi.service.RecommendationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/recommendations")
@RequiredArgsConstructor @Validated @PreAuthorize("hasRole('BUYER')")
public class RecommendationController {
    private final RecommendationService recommendations;
    private final CurrentUserService currentUser;
    @GetMapping
    public RecommendationResponse list(Authentication auth, @RequestParam(defaultValue = "6") @Min(1) @Max(24) int limit) {
        return recommendations.recommend(currentUser.require(auth), limit);
    }
    @GetMapping("/settings")
    public RecommendationSettingsResponse settings(Authentication auth) { return recommendations.settings(currentUser.require(auth)); }
    @PutMapping("/settings")
    public RecommendationSettingsResponse settings(Authentication auth, @Valid @RequestBody RecommendationSettingsRequest request) {
        return recommendations.updateSettings(currentUser.require(auth), request);
    }
    @DeleteMapping("/history") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void clear(Authentication auth) { recommendations.clearHistory(currentUser.require(auth)); }
}
