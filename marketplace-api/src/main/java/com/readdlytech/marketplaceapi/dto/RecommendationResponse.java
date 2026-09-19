package com.readdlytech.marketplaceapi.dto;
import java.util.List;

public record RecommendationResponse(String mode, List<Item> results, String caution) {
    public record Item(PartResponse part, double score, List<String> reasons) {}
}
