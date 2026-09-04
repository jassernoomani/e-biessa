package com.readdlytech.marketplaceapi.controller;

import com.readdlytech.marketplaceapi.dto.PartResponse;
import com.readdlytech.marketplaceapi.entity.User;
import com.readdlytech.marketplaceapi.security.CurrentUserService;
import com.readdlytech.marketplaceapi.service.FavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {
    private final FavoriteService favoriteService;
    private final CurrentUserService currentUserService;

    @PostMapping("/{partId}")
    public ResponseEntity<Void> add(@PathVariable Long partId, Authentication authentication) {
        favoriteService.add(currentUserService.require(authentication), partId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{partId}")
    public ResponseEntity<Void> remove(@PathVariable Long partId, Authentication authentication) {
        favoriteService.remove(currentUserService.require(authentication), partId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<PartResponse>> list(Authentication authentication) {
        User user = currentUserService.require(authentication);
        return ResponseEntity.ok(favoriteService.list(user));
    }
}
