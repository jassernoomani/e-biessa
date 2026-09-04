package com.readdlytech.marketplaceapi.controller;

import com.readdlytech.marketplaceapi.service.AiMarketplaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/ai")
@RequiredArgsConstructor
public class AiAdminController {
    private final AiMarketplaceService aiMarketplaceService;

    @PostMapping("/reindex")
    public Map<String, Object> reindex() {
        int indexed = aiMarketplaceService.reindex();
        return Map.of("indexed", indexed, "message", "Active listings were synchronized with the AI index.");
    }
}

