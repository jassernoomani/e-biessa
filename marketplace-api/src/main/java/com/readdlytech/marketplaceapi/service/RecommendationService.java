package com.readdlytech.marketplaceapi.service;

import com.readdlytech.marketplaceapi.dto.*;
import com.readdlytech.marketplaceapi.entity.*;
import com.readdlytech.marketplaceapi.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Content-based baseline: fresh MySQL candidates, deterministic eligibility, transparent scores. */
@Service @RequiredArgsConstructor
public class RecommendationService {
    private final UserRepository users;
    private final VehicleRepository vehicles;
    private final PartRepository parts;
    private final FavoriteRepository favorites;
    private final ConversationRepository conversations;
    private final BuyerInteractionRepository interactions;
    private final PartService partService;

    @Transactional(readOnly = true)
    public RecommendationSettingsResponse settings(User actor) {
        return settingsResponse(buyer(actor, false));
    }

    @Transactional
    public RecommendationSettingsResponse updateSettings(User actor, RecommendationSettingsRequest request) {
        User buyer = buyer(actor, true);
        var ids = new LinkedHashSet<>(request.vehicleIds());
        var selected = vehicles.findAllById(ids);
        if (selected.size() != ids.size()) throw new IllegalArgumentException("Choose vehicles from the available catalog");
        buyer.setPreferredVehicles(new ArrayList<>(selected));
        buyer.setPersonalizationEnabled(request.personalizationEnabled());
        if (!request.personalizationEnabled()) clearActivity(buyer);
        return settingsResponse(buyer);
    }

    @Transactional
    public void clearHistory(User actor) { clearActivity(buyer(actor, true)); }

    private void clearActivity(User buyer) {
        interactions.deleteAll(interactions.findByBuyerIdOrderByOccurredAtDescIdDesc(buyer.getId()));
        buyer.setRecommendationHistorySince(LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public RecommendationResponse recommend(User actor, int limit) {
        User buyer = buyer(actor, false);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusDays(90);
        if (buyer.getRecommendationHistorySince() != null && buyer.getRecommendationHistorySince().isAfter(cutoff))
            cutoff = buyer.getRecommendationHistorySince();
        final LocalDateTime since = cutoff;
        List<Signal> signals = new ArrayList<>();
        Set<Long> excluded = new HashSet<>();
        // Saved listings stay in the Saved page rather than taking up discovery slots.
        List<Favorite> saved = favorites.findByUser(buyer);
        saved.forEach(f -> excluded.add(f.getPart().getId()));
        if (Boolean.TRUE.equals(buyer.getPersonalizationEnabled())) {
            saved.stream().filter(f -> recent(f.getCreatedAt(), since)).forEach(f ->
                    signals.add(new Signal(categoryId(f.getPart()), null, 12 * decay(f.getCreatedAt(), now),
                            "Based on saved parts in this category")));
            conversations.findByBuyerIdOrSellerIdOrderByUpdatedAtDesc(buyer.getId(), buyer.getId()).stream()
                    .filter(c -> c.getBuyer().getId().equals(buyer.getId()))
                    // A seller reply must not revive buyer history after the buyer clears it.
                    .filter(c -> recent(c.getCreatedAt(), since)).forEach(c -> {
                        excluded.add(c.getPart().getId());
                        signals.add(new Signal(categoryId(c.getPart()), c.getSeller().getId(),
                                8 * decay(c.getCreatedAt(), now), "From a seller you messaged"));
                    });
            interactions.findByBuyerIdOrderByOccurredAtDescIdDesc(buyer.getId()).stream()
                    .filter(r -> recent(r.getOccurredAt(), since)).forEach(r -> {
                        double weight = switch (r.getKind()) { case VIEW -> 3; case SEARCH -> 4; case CONTACT -> 6; };
                        String reason = switch (r.getKind()) {
                            case VIEW -> "Based on recently viewed categories";
                            case SEARCH -> "Based on categories in your recent search results";
                            case CONTACT -> "From a seller whose contact options you opened";
                        };
                        signals.add(new Signal(r.getCategoryId(), r.getKind() == BuyerInteraction.Kind.CONTACT ? r.getSellerId() : null,
                                weight * decay(r.getOccurredAt(), now), reason));
                    });
        }
        List<Ranked> ranked = new ArrayList<>();
        for (Part part : parts.findByStatus(PartStatus.ACTIVE)) {
            if (Boolean.TRUE.equals(part.getSeller().getSuspended()) || part.getSeller().getId().equals(buyer.getId())
                    || excluded.contains(part.getId())) continue;
            List<String> reasons = new ArrayList<>();
            double score = 0;
            if (!buyer.getPreferredVehicles().isEmpty()) {
                Vehicle match = buyer.getPreferredVehicles().stream().filter(v -> matchesVehicle(part, v)).findFirst().orElse(null);
                // No silent widening when a buyer explicitly selected a vehicle.
                if (match == null) continue;
                score += 40;
                reasons.add("Listed for your " + match.getBrand() + " " + match.getModel()
                        + (match.getYear() == null ? "" : " " + match.getYear()));
            }
            double interest = 0;
            // Repeated actions never stack for the same reason; total behavioral influence is capped.
            Map<String, Double> evidence = new LinkedHashMap<>();
            for (Signal signal : signals) {
                boolean related = signal.sellerId() != null ? signal.sellerId().equals(part.getSeller().getId())
                        : signal.categoryId() != null && signal.categoryId().equals(categoryId(part));
                if (related) evidence.merge(signal.reason(), signal.weight(), Math::max);
            }
            for (var entry : evidence.entrySet()) { interest += entry.getValue(); reasons.add(entry.getKey()); }
            score += Math.min(30, interest);
            long saves = favorites.countByPartId(part.getId());
            score += Math.min(5, saves);
            if (saves > 0) reasons.add("Saved by marketplace buyers");
            double freshness = 5 * decay(part.getCreatedAt(), now);
            score += freshness;
            if (reasons.isEmpty()) reasons.add("Recent listing to explore");
            ranked.add(new Ranked(part, score, reasons.stream().limit(4).toList()));
        }
        ranked.sort(Comparator.comparingDouble(Ranked::score).reversed()
                .thenComparing(r -> r.part().getId(), Comparator.reverseOrder()));
        // Round-robin the remainder after two per seller to avoid a single seller taking every slot.
        List<Ranked> diverse = new ArrayList<>(), deferred = new ArrayList<>();
        Map<Long, Integer> sellerCounts = new HashMap<>();
        for (Ranked row : ranked) {
            int count = sellerCounts.merge(row.part().getSeller().getId(), 1, Integer::sum);
            (count <= 2 ? diverse : deferred).add(row);
        }
        diverse.addAll(deferred);
        String mode = !signals.isEmpty() ? "PERSONALIZED" : !buyer.getPreferredVehicles().isEmpty() ? "VEHICLE" : "DISCOVER";
        return new RecommendationResponse(mode, diverse.stream().limit(limit)
                .map(r -> new RecommendationResponse.Item(partService.toResponse(r.part(), buyer.getId()),
                        Math.round(r.score() * 100.0) / 100.0, r.reasons())).toList(),
                "Recommendations use listing information, not a fitment guarantee. Confirm engine, fuel and OEM number with the seller.");
    }

    static boolean matchesVehicle(Part part, Vehicle preferred) {
        Collection<Vehicle> declared = part.getCompatibleVehicles().isEmpty()
                ? part.getVehicle() == null ? List.of() : List.of(part.getVehicle()) : part.getCompatibleVehicles();
        return declared.stream().anyMatch(v -> equal(preferred.getBrand(), v.getBrand())
                && equal(preferred.getModel(), v.getModel())
                && (preferred.getYear() == null || preferred.getYear().equals(v.getYear()))
                && (blank(preferred.getEngine()) || equal(preferred.getEngine(), v.getEngine()))
                && (blank(preferred.getFuelType()) || equal(preferred.getFuelType(), v.getFuelType()))
                && permits(part.getFuelCompatibility(), preferred.getFuelType())
                && permits(part.getEngineCompatibility(), preferred.getEngine()));
    }
    private static boolean permits(String declared, String requested) {
        if (blank(declared) || blank(requested)) return true;
        String normalized = norm(declared);
        return normalized.equals("all") || normalized.equals("universal")
                || Arrays.stream(normalized.split("[,;/|]" )).anyMatch(value -> value.trim().equals(norm(requested)));
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String norm(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " "); }
    private static boolean equal(String a, String b) { return norm(a).equals(norm(b)); }
    private static Long categoryId(Part part) { return part.getCategory() == null ? null : part.getCategory().getId(); }
    private static boolean recent(LocalDateTime date, LocalDateTime since) { return date != null && !date.isBefore(since); }
    private static double decay(LocalDateTime date, LocalDateTime now) {
        return date == null ? 0 : Math.pow(0.5, Math.max(0, ChronoUnit.DAYS.between(date, now)) / 30.0);
    }
    private User buyer(User actor, boolean lock) {
        User user = (lock ? users.lockById(actor.getId()) : users.findById(actor.getId())).orElseThrow();
        if (user.getRole() != UserRole.BUYER || Boolean.TRUE.equals(user.getSuspended()))
            throw new AccessDeniedException("Recommendations are available to active buyers only");
        return user;
    }
    private RecommendationSettingsResponse settingsResponse(User buyer) {
        return new RecommendationSettingsResponse(Boolean.TRUE.equals(buyer.getPersonalizationEnabled()),
                buyer.getPreferredVehicles().stream().sorted(Comparator.comparing(Vehicle::getId)).map(VehicleResponse::from).toList());
    }
    private record Signal(Long categoryId, Long sellerId, double weight, String reason) {}
    private record Ranked(Part part, double score, List<String> reasons) {}
}
