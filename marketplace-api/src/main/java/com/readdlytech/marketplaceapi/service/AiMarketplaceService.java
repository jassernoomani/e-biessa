package com.readdlytech.marketplaceapi.service;

import com.readdlytech.marketplaceapi.dto.*;
import com.readdlytech.marketplaceapi.entity.Part;
import com.readdlytech.marketplaceapi.entity.PartStatus;
import com.readdlytech.marketplaceapi.entity.User;
import com.readdlytech.marketplaceapi.entity.UserRole;
import com.readdlytech.marketplaceapi.repository.PartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiMarketplaceService {
    private final AiServiceClient aiServiceClient;
    private final PartRepository partRepository;

    public AiVisionResponse identifyPartImage(byte[] image, MediaType mediaType) {
        return aiServiceClient.identifyPartImage(image, mediaType);
    }
    private final PartService partService;
    private final RecommendationService recommendationService;

    @Transactional(readOnly = true)
    public int reindex() {
        List<AiListingDocument> documents = partRepository.findByStatus(PartStatus.ACTIVE).stream()
                .map(part -> AiListingDocument.from(partService.toResponse(part, null)))
                .toList();
        return aiServiceClient.replaceAll(documents);
    }

    @Transactional(readOnly = true)
    public SemanticSearchResponse search(String query, int limit, double minimumScore, AiSearchFilter filters, Long userId) {
        AiServiceSearchResponse response = aiServiceClient.search(query, limit, minimumScore, filters);
        List<Long> ids = response.hits().stream().map(AiSearchHit::listingId).toList();
        Map<Long, Part> parts = partRepository.findAllById(ids).stream()
                .filter(part -> part.getStatus() == PartStatus.ACTIVE)
                .collect(Collectors.toMap(Part::getId, Function.identity()));
        List<SemanticPartResult> results = response.hits().stream()
                .filter(hit -> parts.containsKey(hit.listingId()))
                .map(hit -> new SemanticPartResult(partService.toResponse(parts.get(hit.listingId()), userId),
                        hit.score(), hit.matchReasons() == null ? List.of() : hit.matchReasons()))
                .toList();
        GroundedAnswerResponse answer = deterministicAnswer(results);
        return new SemanticSearchResponse(response.query(), results, response.tookMs(),
                response.extractedConstraints() == null ? Map.of() : response.extractedConstraints(),
                answer);
    }

    @Transactional(readOnly = true)
    public MarketplaceAssistantResponse assist(String query, int limit, double minimumScore,
                                                AiSearchFilter filters, User actor) {
        List<AiRecommendationCandidate> candidates = actor.getRole() == UserRole.BUYER
                ? recommendationService.recommend(actor, Math.min(24, Math.max(limit, 12))).results().stream()
                    .map(item -> new AiRecommendationCandidate(item.part().getId(), item.score(), item.reasons()))
                    .toList()
                : List.of();
        AiServiceAssistantResponse response = aiServiceClient.assist(query, limit, minimumScore, filters, candidates);
        List<Long> ids = response.results() == null ? List.of()
                : response.results().stream().map(AiAssistantResult::listingId).distinct().toList();
        Map<Long, Part> parts = partRepository.findAllById(ids).stream()
                .filter(part -> part.getStatus() == PartStatus.ACTIVE)
                .filter(part -> !Boolean.TRUE.equals(part.getSeller().getSuspended()))
                .collect(Collectors.toMap(Part::getId, Function.identity()));
        List<AssistantPartResult> results = response.results() == null ? List.of() : response.results().stream()
                .filter(result -> parts.containsKey(result.listingId()))
                .map(result -> new AssistantPartResult(partService.toResponse(parts.get(result.listingId()), actor.getId()),
                        Math.max(0, Math.min(1, result.score())),
                        result.matchReasons() == null ? List.of() : result.matchReasons(),
                        "RECOMMENDATION".equals(result.source()) ? "RECOMMENDATION" : "SEARCH"))
                .toList();
        String intent = switch (response.intent() == null ? "SEARCH" : response.intent()) {
            case "COMPATIBILITY" -> "COMPATIBILITY";
            case "RECOMMENDATION" -> "RECOMMENDATION";
            default -> "SEARCH";
        };
        List<String> allowedTools = List.of("search_agent", "compatibility_agent", "recommendation_agent");
        List<String> tools = response.toolsUsed() == null ? List.of() : response.toolsUsed().stream()
                .filter(allowedTools::contains).distinct().toList();
        return new MarketplaceAssistantResponse(query, intent, results, response.tookMs(),
                response.extractedConstraints() == null ? Map.of() : response.extractedConstraints(), tools,
                deterministicAssistantAnswer(results, intent));
    }

    private GroundedAnswerResponse deterministicAnswer(List<SemanticPartResult> results) {
        if (results.isEmpty()) {
            return new GroundedAnswerResponse(
                    "No active listing met all verified compatibility requirements.",
                    List.of(),
                    List.of("Try removing one optional constraint or ask a seller to confirm an alternative part."),
                    "deterministic");
        }
        PartResponse best = results.getFirst().part();
        String noun = results.size() == 1 ? "listing" : "listings";
        String summary = "Found %d active compatibility-checked %s. The strongest current match is Listing #%d: %s."
                .formatted(results.size(), noun, best.getId(), best.getTitle());
        return new GroundedAnswerResponse(
                summary,
                results.stream().limit(3).map(result -> result.part().getId()).toList(),
                List.of("Confirm the OEM or interchange number and exact fitment with the seller before buying."),
                "deterministic");
    }

    private GroundedAnswerResponse deterministicAssistantAnswer(List<AssistantPartResult> results, String intent) {
        if (results.isEmpty()) return new GroundedAnswerResponse(
                "No active listing met the assistant's verified requirements.", List.of(),
                List.of("Try removing one optional constraint or ask a seller to confirm an alternative part."),
                "deterministic");
        PartResponse best = results.getFirst().part();
        String basis = "RECOMMENDATION".equals(intent)
                ? "your saved preferences and marketplace activity"
                : "the requested compatibility constraints";
        String summary = "Found %d active option%s using %s. The strongest current match is Listing #%d: %s."
                .formatted(results.size(), results.size() == 1 ? "" : "s", basis, best.getId(), best.getTitle());
        return new GroundedAnswerResponse(summary,
                results.stream().limit(3).map(result -> result.part().getId()).toList(),
                List.of("Confirm the OEM or interchange number and exact fitment with the seller before buying."),
                "deterministic");
    }
}
