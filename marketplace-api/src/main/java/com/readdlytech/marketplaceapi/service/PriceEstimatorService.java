package com.readdlytech.marketplaceapi.service;

import com.readdlytech.marketplaceapi.dto.PriceEstimateRequest;
import com.readdlytech.marketplaceapi.dto.PriceEstimateResponse;
import com.readdlytech.marketplaceapi.entity.Part;
import com.readdlytech.marketplaceapi.entity.PartStatus;
import com.readdlytech.marketplaceapi.entity.Vehicle;
import com.readdlytech.marketplaceapi.repository.PartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PriceEstimatorService {
    private static final Set<String> TITLE_STOP_WORDS = Set.of(
            "the", "for", "and", "with", "part", "parts", "piece", "pieces", "auto", "car",
            "pour", "avec", "voiture", "mta3", "mtaa", "front", "rear", "avant", "arriere",
            "used", "good", "fair", "new", "original", "oem"
    );
    private final PartRepository parts;

    @Transactional(readOnly = true)
    public PriceEstimateResponse estimate(PriceEstimateRequest request) {
        if (!hasText(request.title()) && request.categoryId() == null && request.vehicleIds().isEmpty()
                && !hasText(request.oemNumber())) {
            throw new IllegalArgumentException("Add a title, category, vehicle, or OEM number before estimating a price.");
        }
        Set<String> requestedTokens = tokens(request.title());
        List<ScoredPart> comparable = parts.findByStatus(PartStatus.ACTIVE).stream()
                .filter(part -> part.getPrice() != null && part.getPrice().signum() > 0)
                .filter(part -> request.excludeListingId() == null || !request.excludeListingId().equals(part.getId()))
                .filter(part -> part.getSeller() == null || !Boolean.TRUE.equals(part.getSeller().getSuspended()))
                .map(part -> score(part, request, requestedTokens))
                .filter(scored -> scored.score() >= 5 && scored.hasIdentitySignal())
                .sorted(Comparator.comparingInt(ScoredPart::score).reversed()
                        .thenComparing(scored -> scored.part().getPrice()))
                .limit(20)
                .toList();

        if (comparable.isEmpty()) {
            return new PriceEstimateResponse(null, null, null, 0, "INSUFFICIENT", List.of(), List.of(),
                    "No sufficiently similar active listings were found. Set the price manually and compare exact OEM fitment.");
        }

        List<BigDecimal> prices = comparable.stream().map(scored -> scored.part().getPrice()).sorted().toList();
        BigDecimal suggested = percentile(prices, 0.50);
        BigDecimal lower = percentile(prices, 0.25);
        BigDecimal upper = percentile(prices, 0.75);
        double averageScore = comparable.stream().mapToInt(ScoredPart::score).average().orElse(0);
        String confidence = comparable.size() >= 8 && averageScore >= 8 ? "HIGH"
                : comparable.size() >= 4 ? "MEDIUM" : "LOW";

        List<String> reasons = new ArrayList<>();
        reasons.add(comparable.size() + " active comparable listing" + (comparable.size() == 1 ? "" : "s"));
        if (comparable.stream().anyMatch(ScoredPart::sameOem)) reasons.add("Exact OEM number represented");
        if (comparable.stream().anyMatch(ScoredPart::sameVehicle)) reasons.add("Shared vehicle compatibility represented");
        if (comparable.stream().anyMatch(ScoredPart::sameCategory)) reasons.add("Same part category represented");
        if (!requestedTokens.isEmpty()) reasons.add("Listing-title part terms compared");
        return new PriceEstimateResponse(suggested, lower, upper, comparable.size(), confidence,
                comparable.stream().map(scored -> scored.part().getId()).toList(), reasons,
                "This is a local listing comparison, not a valuation or guaranteed sale price. Condition, authenticity, and exact fitment can materially change value.");
    }

    private ScoredPart score(Part part, PriceEstimateRequest request, Set<String> requestedTokens) {
        boolean sameCategory = request.categoryId() != null && part.getCategory() != null
                && request.categoryId().equals(part.getCategory().getId());
        boolean sameOem = hasText(request.oemNumber()) && equal(request.oemNumber(), part.getOemNumber());
        boolean sameVehicle = sharesVehicle(request.vehicleIds(), part);
        Set<String> sharedTokens = new HashSet<>(requestedTokens);
        sharedTokens.retainAll(tokens(part.getTitle()));
        boolean titleMatch = sharedTokens.size() >= 2 || (requestedTokens.size() == 1 && sharedTokens.size() == 1);

        int score = sameOem ? 12 : 0;
        if (sameCategory) score += 5;
        if (sameVehicle) score += 6;
        score += Math.min(6, sharedTokens.size() * 2);
        if (hasText(request.partPosition()) && equal(request.partPosition(), part.getPartPosition())) score += 2;
        if (hasText(request.partBrand()) && equal(request.partBrand(), part.getPartBrand())) score += 2;
        if (hasText(request.condition()) && equal(request.condition(), part.getCondition())) score += 1;
        return new ScoredPart(part, score, sameOem || titleMatch || sameCategory,
                sameOem, sameVehicle, sameCategory);
    }

    private boolean sharesVehicle(Set<Long> requestedIds, Part part) {
        if (requestedIds.isEmpty()) return false;
        if (part.getVehicle() != null && requestedIds.contains(part.getVehicle().getId())) return true;
        return part.getCompatibleVehicles().stream().map(Vehicle::getId).anyMatch(requestedIds::contains);
    }


    private BigDecimal percentile(List<BigDecimal> values, double percentile) {
        int index = (int) Math.round((values.size() - 1) * percentile);
        return values.get(index).setScale(2, RoundingMode.HALF_UP);
    }

    private Set<String> tokens(String value) {
        if (!hasText(value)) return Set.of();
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ");
        Set<String> result = new HashSet<>();
        Arrays.stream(normalized.split("\\s+")).filter(token -> token.length() >= 3)
                .filter(token -> !TITLE_STOP_WORDS.contains(token)).forEach(result::add);
        return result;
    }

    private boolean equal(String left, String right) {
        return hasText(left) && hasText(right) && left.trim().equalsIgnoreCase(right.trim());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record ScoredPart(Part part, int score, boolean hasIdentitySignal,
                              boolean sameOem, boolean sameVehicle, boolean sameCategory) {}
}
